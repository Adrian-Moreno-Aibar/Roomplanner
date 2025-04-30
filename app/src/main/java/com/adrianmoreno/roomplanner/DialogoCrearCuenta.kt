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
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore

class DialogoCrearCuenta : DialogFragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_dialogo_crear_cuenta, container, false)

        val emailEt   = view.findViewById<EditText>(R.id.emailEditText)
        val passEt    = view.findViewById<EditText>(R.id.passwordEditText)
        val btnSignUp = view.findViewById<Button>(R.id.signupButton)

        btnSignUp.setOnClickListener {
            val email = emailEt.text.toString().trim()
            val pass  = passEt.text.toString()

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "Correo inválido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pass.length < 6) {
                Toast.makeText(requireContext(),
                    "La contraseña debe tener al menos 6 caracteres",
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Enviar verificación y guardar perfil
                        auth.currentUser?.sendEmailVerification()
                        saveUserProfile()
                    } else {
                        val ex = task.exception
                        val msg = when (ex) {
                            is FirebaseAuthUserCollisionException ->
                                "Ese correo ya está registrado"
                            is FirebaseNetworkException ->
                                "Error de red, inténtalo más tarde"
                            else ->
                                "Error al crear cuenta: ${ex?.localizedMessage}"
                        }
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                }
        }

        return view
    }

    private fun saveUserProfile() {
        val uid   = auth.currentUser!!.uid
        val email = auth.currentUser!!.email ?: ""
        val user  = User(uid = uid, email = email, role = "ADMIN", hotelRefs = emptyList())

        db.collection("users").document(uid)
            .set(user)
            .addOnSuccessListener {
                Toast.makeText(requireContext(),
                    "Cuenta creada correctamente. Verifica tu correo.",
                    Toast.LENGTH_LONG).show()
                dismiss()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(),
                    "Error guardando perfil: ${e.localizedMessage}",
                    Toast.LENGTH_LONG).show()
            }
    }
}
