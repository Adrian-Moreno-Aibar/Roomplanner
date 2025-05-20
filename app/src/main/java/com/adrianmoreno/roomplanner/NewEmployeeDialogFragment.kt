package com.adrianmoreno.roomplanner

import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.adrianmoreno.roomplanner.controller.UserController
import com.adrianmoreno.roomplanner.models.User
import com.google.firebase.auth.FirebaseAuth

class NewEmployeeDialogFragment(
    private val hotelId: String,
    private val controller: UserController
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_new_employee, null)

        val nameEt  = view.findViewById<EditText>(R.id.etName)
        val emailEt = view.findViewById<EditText>(R.id.etEmail)

        return AlertDialog.Builder(requireContext())
            .setTitle("Nuevo empleado")
            .setView(view)
            .setPositiveButton("Crear") { _, _ ->
                val name  = nameEt.text.toString().trim()
                val email = emailEt.text.toString().trim()

                if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email)) {
                    Toast.makeText(context, "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Creamos cuenta en Firebase Auth con contraseña temporal = email
                FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(email, email)
                    .addOnSuccessListener { authRes ->
                        // Forzamos reset para que el user defina contraseña real
                        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                        val uid = authRes.user!!.uid
                        // Creamos perfil en Firestore
                        val user = User(
                            uid       = uid,
                            name      = name,
                            email     = email,
                            role      = "CLEANER",
                            hotelRefs = listOf(hotelId)
                        )
                        controller.addUser(user)
                        Toast.makeText(context,
                            "Empleado invitado. Revisa tu correo para definir contraseña",
                            Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context,
                            "Error creando empleado: ${e.message}",
                            Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .create()
    }
}
