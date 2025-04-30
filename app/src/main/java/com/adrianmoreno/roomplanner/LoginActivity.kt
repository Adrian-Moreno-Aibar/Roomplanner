package com.adrianmoreno.roomplanner

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.adrianmoreno.roomplanner.models.User
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.*

class LoginActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        val errorTv = findViewById<TextView>(R.id.errorTextView)
        val emailEt   = findViewById<EditText>(R.id.emailEditText)
        val passEt    = findViewById<EditText>(R.id.passwordEditText)
        val btnLogin  = findViewById<Button>(R.id.loginButton)
        val btnSignUp = findViewById<Button>(R.id.signupButton)
        val forgotPasswordText = findViewById<TextView>(R.id.forgotPasswordText)

        btnSignUp.setOnClickListener {
            DialogoCrearCuenta().show(supportFragmentManager, "CrearCuenta")
        }

        btnLogin.setOnClickListener {
            val email = emailEt.text.toString().trim()
            val pass  = passEt.text.toString()

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Introduce un correo válido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pass.isEmpty()) {
                Toast.makeText(this, "La contraseña no puede estar vacía", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onLoginSuccess()
                    } else {
                        val ex = task.exception
                        val msg = when (ex) {
                            is FirebaseAuthInvalidUserException ->
                                "No existe ningún usuario con ese correo"
                            is FirebaseAuthInvalidCredentialsException ->
                                "Contraseña incorrecta"
                            is FirebaseNetworkException ->
                                "Error de red, comprueba tu conexión"
                            is FirebaseAuthEmailException  ->
                                "Ningún usuario con ese correo"
                            else ->
                                "Credenciales no válidas"
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()


                    }
                }
        }

        forgotPasswordText.setOnClickListener {
            val email = emailEt.text.toString().trim()
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Introduce un correo válido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this,
                            "Se ha enviado un correo de recuperación",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val ex = task.exception
                        val msg = when (ex) {
                            is FirebaseAuthInvalidUserException ->
                                "No existe ningún usuario con ese correo"
                            is FirebaseNetworkException ->
                                "Error de red, no se pudo enviar el correo"
                            else ->
                                "Error al enviar correo de recuperación"
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun onLoginSuccess() {
        val uid = auth.currentUser!!.uid
        // Leer perfil de usuario para saber rol y hoteles
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                val user = snap.toObject(User::class.java)
                if (user == null) {
                    Toast.makeText(this,
                        "Perfil de usuario no encontrado",
                        Toast.LENGTH_LONG).show()
                    auth.signOut()
                    return@addOnSuccessListener
                }
                // Redirigir a HomeActivity
                val intent = Intent(this, HomeActivity::class.java).apply {
                    putExtra("USER_ROLE", user.role)
                    putStringArrayListExtra(
                        "USER_HOTELS",
                        ArrayList(user.hotelRefs)
                    )
                }
                startActivity(intent)
                finish()
            }
    }
}
