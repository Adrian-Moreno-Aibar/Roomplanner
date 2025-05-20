package com.adrianmoreno.roomplanner

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.adrianmoreno.roomplanner.models.User
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthEmailException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 0) Si ya hay usuario autenticado, cargar su perfil y saltar
        auth.currentUser?.let { u ->
            db.collection("users").document(u.uid).get()
                .addOnSuccessListener { snap ->
                    val user = snap.toObject(User::class.java)
                    if (user != null) {
                        startDashBoardActivity(user.role, user.hotelRefs)
                    } else {
                        auth.signOut()
                        initLoginScreen()
                    }
                }
                .addOnFailureListener {
                    auth.signOut()
                    initLoginScreen()
                }
            return
        }

        // 1) No había sesión: mostrar login
        initLoginScreen()
    }

    private fun initLoginScreen() {
        setContentView(R.layout.activity_login)

        val emailEt           = findViewById<EditText>(R.id.emailEditText)
        val passEt            = findViewById<EditText>(R.id.passwordEditText)
        val btnLogin          = findViewById<Button>(R.id.loginButton)
        val btnSignUp         = findViewById<Button>(R.id.signupButton)
        val forgotPasswordTxt = findViewById<TextView>(R.id.forgotPasswordText)
        val inviteCodeTxt     = findViewById<TextView>(R.id.inviteCodeText)

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
                            is FirebaseAuthEmailException ->
                                "Ningún usuario con ese correo"
                            else ->
                                "Credenciales no válidas"
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }
        }

        forgotPasswordTxt.setOnClickListener {
            val email = emailEt.text.toString().trim()
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Introduce un correo válido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(
                            this,
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

        // NUEVO: opción de pegar código de invitación
        inviteCodeTxt.setOnClickListener {
            val input = EditText(this).apply {
                hint = "Pega aquí tu código"
            }
            AlertDialog.Builder(this)
                .setTitle("Código de invitación")
                .setView(input)
                .setPositiveButton("Aceptar") { _, _ ->
                    val token = input.text.toString().trim()
                    if (token.isEmpty()) {
                        Toast.makeText(this, "Introduce un código válido", Toast.LENGTH_SHORT).show()
                    } else {
                        startActivity(
                            Intent(this, AcceptInvitationActivity::class.java)
                                .putExtra("INV_TOKEN", token)
                        )
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun onLoginSuccess() {
        val uid = auth.currentUser!!.uid
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                val user = snap.toObject(User::class.java)
                if (user == null) {
                    Toast.makeText(
                        this,
                        "Perfil de usuario no encontrado",
                        Toast.LENGTH_LONG
                    ).show()
                    auth.signOut()
                } else {
                    startDashBoardActivity(user.role, user.hotelRefs)
                }
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Error leyendo perfil: ${it.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
                auth.signOut()
            }
    }

    private fun startDashBoardActivity(role: String, hotelRefs: List<String>) {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra("USER_ROLE", role)
            putStringArrayListExtra("USER_HOTELS", ArrayList(hotelRefs))
        }
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }
}
