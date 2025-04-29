// com/example/roomplanner/ui/LoginActivity.kt
package com.adrianmoreno.roomplanner

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.adrianmoreno.roomplanner.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val emailEt   = findViewById<EditText>(R.id.emailEditText)
        val passEt    = findViewById<EditText>(R.id.passwordEditText)
        val btnLogin  = findViewById<Button>(R.id.loginButton)
        val btnSignUp = findViewById<Button>(R.id.signupButton)
        var forgotPasswordText = findViewById<TextView>(R.id.forgotPasswordText)

        btnSignUp.setOnClickListener {
            DialogoCrearCuenta().show(supportFragmentManager, "CrearCuenta")
        }

        btnLogin.setOnClickListener {
            val email = emailEt.text.toString().trim()
            val pass  = passEt.text.toString()
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Correo inválido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pass.isEmpty()) {
                Toast.makeText(this, "Contraseña requerida", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Toast.makeText(this,
                            "Error de autenticación: ${task.exception?.message}",
                            Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }
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
                            // Redirigir a HomeActivity pasando el objeto User
                            val intent = Intent(this, HomeActivity::class.java)
                            intent.putExtra("USER_ROLE", user.role)
                            intent.putStringArrayListExtra(
                                "USER_HOTELS",
                                ArrayList(user.hotelRefs)
                            )
                            startActivity(intent)
                            finish()
                        }
                }
        }
        forgotPasswordText.setOnClickListener {
            val correo = findViewById<EditText>(R.id.emailEditText).text.toString()

            if (correo.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                firebase_reset_password(correo)
            } else {
                Toast.makeText(this, "Ingresa un correo válido", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun firebase_reset_password(correo: String) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(correo)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Correo de recuperación enviado", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Error al enviar el correo", Toast.LENGTH_LONG).show()
                }
            }
    }
}
