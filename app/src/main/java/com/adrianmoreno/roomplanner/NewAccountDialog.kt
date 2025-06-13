package com.adrianmoreno.roomplanner

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.adrianmoreno.roomplanner.models.User
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

class NewAccountDialog : DialogFragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflamos el layout del diálogo
        val view = inflater.inflate(R.layout.activity_dialog_new_account, container, false)

        val nameEt    = view.findViewById<EditText>(R.id.nameEditText)
        val emailEt   = view.findViewById<EditText>(R.id.emailEditText)
        val passEt    = view.findViewById<EditText>(R.id.passwordEditText)
        val btnSignUp = view.findViewById<Button>(R.id.signupButton)

        btnSignUp.setOnClickListener {
            val name  = nameEt.text.toString().trim()
            val email = emailEt.text.toString().trim()
            val pass  = passEt.text.toString()

            // Validaciones básicas
            if (name.isEmpty()) {
                context?.let { ctx ->
                    Toast.makeText(ctx, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                context?.let { ctx ->
                    Toast.makeText(ctx, "Correo inválido", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }
            if (pass.length < 6) {
                context?.let { ctx ->
                    Toast.makeText(ctx,
                        "La contraseña debe tener al menos 6 caracteres",
                        Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }

            // Deshabilitar el botón para evitar múltiples clics (opcional)
            btnSignUp.isEnabled = false

            // Usamos coroutines para simplificar y manejar cancelación si el fragmento se destruye
            lifecycleScope.launch {
                try {
                    // 1) Crear cuenta en Firebase Auth
                    val authResult = auth.createUserWithEmailAndPassword(email, pass).await()
                    val firebaseUser = authResult.user
                    val uid = firebaseUser?.uid
                    if (uid.isNullOrEmpty()) {
                        throw Exception("UID nulo tras crear cuenta")
                    }

                    // 2) Enviar verificación de email
                    try {
                        firebaseUser.sendEmailVerification().await()
                    } catch (_: Exception) {
                        // Si falla el envío de verificación, seguimos de todos modos a guardar perfil
                    }

                    // 3) Guardar perfil en Firestore
                    val user = User(
                        uid       = uid,
                        name      = name,
                        email     = email,
                        role      = "ADMIN",
                        hotelRefs = emptyList(),
                        photoUrl  = ""
                    )
                    db.collection("users").document(uid).set(user).await()

                    // 4) Sign out después de guardar perfil
                    auth.signOut()

                    // Si el fragmento aún está agregado, mostrar Toast y cerrar diálogo
                    if (isAdded) {
                        context?.let { ctx ->
                            Toast.makeText(
                                ctx,
                                "Cuenta creada. Revisa tu correo para verificarla antes de entrar.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        dismiss()
                    }
                } catch (e: Exception) {
                    // Reactivar el botón
                    if (isAdded) {
                        btnSignUp.isEnabled = true
                    }
                    // Determinar mensaje de error
                    val msg = when (val ex = e) {
                        is FirebaseAuthUserCollisionException ->
                            "Ese correo ya está registrado"
                        is FirebaseNetworkException ->
                            "Error de red, inténtalo más tarde"
                        else ->
                            // e.localizedMessage puede ser null
                            ex.localizedMessage?.let { "Error: $it" }
                                ?: "Error desconocido al crear cuenta"
                    }
                    if (isAdded) {
                        context?.let { ctx ->
                            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        return view
    }

}
