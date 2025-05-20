package com.adrianmoreno.roomplanner

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.adrianmoreno.roomplanner.models.User
import com.adrianmoreno.roomplanner.repositories.InvitationRepository
import com.adrianmoreno.roomplanner.repositories.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AcceptInvitationActivity : AppCompatActivity() {

    private lateinit var token: String
    private val invRepo  = InvitationRepository()
    private val userRepo = UserRepository()
    private val auth     = FirebaseAuth.getInstance()
    private val db       = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accept_invitation)

        token = intent.getStringExtra("INV_TOKEN") ?: run {
            finish(); return
        }

        val nameEt  = findViewById<EditText>(R.id.etName)
        val emailEt = findViewById<EditText>(R.id.etEmail)
        val passEt  = findViewById<EditText>(R.id.etPassword)
        val btn     = findViewById<Button>(R.id.btnAccept)

        // Fetch invitación de forma suspend
        lifecycleScope.launch {
            val inv = invRepo.getInvitationSuspend(token)
            if (inv == null || inv.used || inv.expiresAt.seconds < com.google.firebase.Timestamp.now().seconds) {
                runOnUiThread {
                    Toast.makeText(this@AcceptInvitationActivity,
                        "Invitación inválida o caducada", Toast.LENGTH_LONG).show()
                    finish()
                }
                return@launch
            }
            // Pre‑llenar campos en main thread
            runOnUiThread {
                nameEt.setText(inv.name)
                emailEt.setText(inv.email)
            }

            // Listener del botón de registro
            btn.setOnClickListener {
                val name  = nameEt.text.toString().trim()
                val email = emailEt.text.toString().trim()
                val pass  = passEt.text.toString()
                if (name.isEmpty() || email.isEmpty() || pass.length < 6) {
                    Toast.makeText(
                        this@AcceptInvitationActivity,
                        "Completa todos los campos (contraseña ≥ 6)",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    try {
                        // 1) Crear cuenta
                        auth.createUserWithEmailAndPassword(email, pass).await()
                        val uid = auth.currentUser!!.uid

                        // 2) Crear perfil con el **hotelId correcto**:
                        val user = User(
                            uid       = uid,
                            name      = name,
                            email     = email,
                            role      = "CLEANER",
                            hotelRefs = listOf(inv.hotelId)   // aquí va inv.hotelId
                        )
                        userRepo.createUserProfile(user)

                        // 3) Marcar invitación usada
                        invRepo.markUsed(token)

                        runOnUiThread {
                            Toast.makeText(
                                this@AcceptInvitationActivity,
                                "Cuenta creada con éxito",
                                Toast.LENGTH_LONG
                            ).show()
                            finish()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(
                                this@AcceptInvitationActivity,
                                "Error registro: ${e.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }
}
