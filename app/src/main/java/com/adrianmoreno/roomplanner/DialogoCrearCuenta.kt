// com/example/roomplanner/ui/DialogoCrearCuenta.kt
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
import com.adrianmoreno.roomplanner.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DialogoCrearCuenta : DialogFragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 1. Inflar el layout
        val view = inflater.inflate(R.layout.fragment_dialogo_crear_cuenta, container, false)

        // 2. Referencias a los campos
        val emailEt   = view.findViewById<EditText>(R.id.emailEditText)
        val passEt    = view.findViewById<EditText>(R.id.passwordEditText)
        val btnSignUp = view.findViewById<Button>(R.id.signupButton)

        // 3. Click listener
        btnSignUp.setOnClickListener {
            val email = emailEt.text.toString().trim()
            val pass  = passEt.text.toString()

            // Validaciones básicas
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "Correo inválido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pass.length < 6) {
                Toast.makeText(requireContext(), "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            crearCuentaFirebase(email, pass)
        }

        return view
    }

    private fun crearCuentaFirebase(email: String, password: String) {
        // 1. Crear usuario en Firebase Auth
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Toast.makeText(
                        requireContext(),
                        "Error al crear cuenta: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnCompleteListener
                }

                // 2. Enviar verificación por correo
                auth.currentUser?.sendEmailVerification()
                    ?.addOnCompleteListener { verifTask ->
                        if (!verifTask.isSuccessful) {
                            Toast.makeText(
                                requireContext(),
                                "No se pudo enviar correo de verificación.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                // 3. Guardar perfil en Firestore con rol ADMIN
                val uid = auth.currentUser!!.uid
                val user = User(
                    uid       = uid,
                    email     = email,
                    role      = "ADMIN",
                    hotelRefs = emptyList()
                )
                db.collection("users").document(uid)
                    .set(user)
                    .addOnSuccessListener {
                        Toast.makeText(
                            requireContext(),
                            "Cuenta creada correctamente. Verifica tu correo.",
                            Toast.LENGTH_LONG
                        ).show()
                        dismiss()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            requireContext(),
                            "Error guardando perfil: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
    }
}
