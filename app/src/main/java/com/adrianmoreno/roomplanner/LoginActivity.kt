/**
 * LoginActivity
 * =============
 *
 * Pantalla de autenticación de usuario. Permite:
 * - Iniciar sesión con email y contraseña.
 * - Registro de nuevos usuarios (dialog NewAccountDialog).
 * - Recuperación de contraseña.
 * - Unión a hotel mediante código de invitación.
 */

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

        // Si ya hay un usuario autenticado, obtener perfil y entrar al Dashboard
        auth.currentUser?.let { u ->
            db.collection("users").document(u.uid).get()
                .addOnSuccessListener { snap ->
                    val user = snap.toObject(User::class.java)
                    if (user != null) {
                        // Si el perfil existe, iniciar Dashboard
                        startDashBoardActivity(user.role, user.hotelRefs)
                    } else {
                        // Si el perfil no existe, cerrar sesión y mostrar login
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

        // 1) No hay sesión, inicializar pantalla de login
        initLoginScreen()
    }

    /**
     * Configura la UI de login y los listeners de botones.
     */
    private fun initLoginScreen() {
        setContentView(R.layout.activity_login)

        // Referencias a vistas
        val emailEt           = findViewById<EditText>(R.id.emailEditText)
        val passEt            = findViewById<EditText>(R.id.passwordEditText)
        val btnLogin          = findViewById<Button>(R.id.loginButton)
        val btnSignUp         = findViewById<Button>(R.id.signupButton)
        val forgotPasswordTxt = findViewById<TextView>(R.id.forgotPasswordText)
        val inviteCodeTxt     = findViewById<TextView>(R.id.inviteCodeText)

        // Registrar nuevo usuario con diálogo personalizado
        btnSignUp.setOnClickListener {
            NewAccountDialog().show(supportFragmentManager, "CrearCuenta")
        }

        // Intento de login
        btnLogin.setOnClickListener {
            val email = emailEt.text.toString().trim()
            val pass  = passEt.text.toString()

            // Validar el formato de email
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Introduce un correo válido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Validar que la contraseña no este vacía
            if (pass.isEmpty()) {
                Toast.makeText(this, "La contraseña no puede estar vacía", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Llamada a FirebaseAuth para autenticar
            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser!!
                        if (!user.isEmailVerified) {
                            // Email no verificado: reenviar enlace de verificación
                            user.sendEmailVerification()
                                .addOnSuccessListener {
                                    Toast.makeText(
                                        this,
                                        "Tu correo no está verificado. Se ha reenviado el enlace de verificación.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(
                                        this,
                                        "No se pudo reenviar el correo de verificación: ${it.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            auth.signOut()
                        } else {
                            // Login exitoso y verificado
                            onLoginSuccess()
                        }
                    } else {
                        // Manejo de errores comunes de autenticación
                        val ex = task.exception
                        val msg = when (ex) {
                            is FirebaseAuthInvalidUserException        -> "No existe ningún usuario con ese correo"
                            is FirebaseAuthInvalidCredentialsException -> "Contraseña incorrecta"
                            is FirebaseNetworkException                -> "Error de red, comprueba tu conexión"
                            is FirebaseAuthEmailException              -> "Ningún usuario con ese correo"
                            else                                       -> "Credenciales no válidas"
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }
        }

        // Recuperación de contraseña
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
                        // Errores comunes en envío de reset
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

        // Unión a hotel mediante código de invitación
        inviteCodeTxt.setOnClickListener {
            // Mostrar diálogo con EditText para introducir token
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
                        // Lanza AcceptInvitationActivity con token
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

    /**
     * Tras login exitoso y email verificado, obtener perfil y navegar.
     */
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

    /**
     * Inicia DashboardActivity pasando rol y lista de hoteles.
     */
    private fun startDashBoardActivity(role: String, hotelRefs: List<String>) {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra("USER_ROLE", role)
            putStringArrayListExtra("USER_HOTELS", ArrayList(hotelRefs))
        }
        startActivity(intent)
        finish()
    }

    /**
     * Al pulsar atrás, cerrar la app completamente.
     */
    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }
}
