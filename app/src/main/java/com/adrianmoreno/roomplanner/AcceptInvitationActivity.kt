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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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
            finish()
            return
        }

        val nameEt  = findViewById<EditText>(R.id.etName)
        val emailEt = findViewById<EditText>(R.id.etEmail)
        val passEt  = findViewById<EditText>(R.id.etPassword)
        val btn     = findViewById<Button>(R.id.btnAccept).apply {
            isEnabled = false
        }

        // 1) Cargamos y validamos la invitación
        lifecycleScope.launch {
            val inv = invRepo.getInvitationSuspend(token)
            if (inv == null || inv.used || inv.expiresAt.seconds < Timestamp.now().seconds) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AcceptInvitationActivity,
                        "Invitación inválida o caducada",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
                return@launch
            }

            // 2) Pre-llenamos campos y habilitamos botón
            withContext(Dispatchers.Main) {
                nameEt.setText(inv.name)
                emailEt.setText(inv.email)
                btn.isEnabled = true
            }

            // 3) Listener para aceptar
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

                // 4) Procesamos registro o asociación
                lifecycleScope.launch {
                    try {
                        // ── NUEVO: comprobamos si el email ya existe ──
                        val methods = auth.fetchSignInMethodsForEmail(email).await().signInMethods
                        if (methods.isNullOrEmpty()) {
                            // no existe → creamos usuario nuevo
                            auth.createUserWithEmailAndPassword(email, pass).await()
                            val uid = auth.currentUser!!.uid

                            val newUser = User(
                                uid       = uid,
                                name      = name,
                                email     = email,
                                role      = "CLEANER",
                                hotelRefs = listOf(inv.hotelId)
                            )
                            userRepo.createUserProfile(newUser)
                        } else {
                            // ya existe → solo lo asociamos al hotel
                            val snap = db.collection("users")
                                .whereEqualTo("email", email)
                                .get()
                                .await()
                            val existingUid = snap.documents.firstOrNull()?.id
                            if (existingUid != null) {
                                db.collection("users").document(existingUid)
                                    .update("hotelRefs", FieldValue.arrayUnion(inv.hotelId))
                                    .await()
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@AcceptInvitationActivity,
                                        "Error: usuario no encontrado tras colisión",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                return@launch
                            }
                        }

                        // 5) Marcamos invitación como usada
                        invRepo.markUsed(token)

                        // 6) Feedback y cierre
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@AcceptInvitationActivity,
                                "¡Bienvenido!",
                                Toast.LENGTH_LONG
                            ).show()
                            setResult(RESULT_OK)
                            finish()
                        }

                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@AcceptInvitationActivity,
                                "Correo ya en uso, por favor use el código en su Dashboard",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }
}
