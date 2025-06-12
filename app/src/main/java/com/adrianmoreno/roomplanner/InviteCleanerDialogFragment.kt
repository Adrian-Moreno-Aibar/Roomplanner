package com.adrianmoreno.roomplanner

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.adrianmoreno.roomplanner.repositories.InvitationRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class InviteCleanerDialogFragment(
    private val hotelId: String


) : DialogFragment() {

    private val repo = InvitationRepository()
    private lateinit var dialog: AlertDialog
    private lateinit var nameEt: EditText
    private lateinit var emailEt: EditText

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_new_employee, null)
        nameEt  = view.findViewById(R.id.etName)
        emailEt = view.findViewById(R.id.etEmail)

        dialog = AlertDialog.Builder(requireContext())
            .setTitle("Invitar limpiador")
            .setView(view)
            .setPositiveButton("Invitar", null)
            .setNegativeButton("Cancelar") { _, _ ->
                // el dialog se cierra automáticamente
            }
            .create()

        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name  = nameEt.text.toString().trim()
            val email = emailEt.text.toString().trim()
            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email)) {
                Toast.makeText(context, "Nombre y email son obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val db = FirebaseFirestore.getInstance()

                // 1) Comprobar si ese email ya está asociado a este hotel
                val alreadyInHotel = try {
                    val snap = db.collection("users")
                        .whereEqualTo("email", email)
                        .whereArrayContains("hotelRefs", hotelId)
                        .get()
                        .await()
                    snap.documents.isNotEmpty()
                } catch (e: Exception) {
                    false
                }

                if (alreadyInHotel) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Este usuario ya trabaja en este hotel",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                // 2) Crear invitación
                val token = repo.createInvitation(email, name, hotelId)
                if (token == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Error creando invitación",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                // 3) Leer el nombre del hotel
                val hotelName = try {
                    val snap = db.collection("hotels")
                        .document(hotelId)
                        .get()
                        .await()
                    snap.getString("name") ?: hotelId
                } catch (e: Exception) {
                    hotelId
                }

                // 4) Construir asunto y cuerpo del mensaje
                val subject = "Invitación a RoomPlanner"
                val body = """
                Hola $name,
                
                Has sido invitado como CLEANER en el hotel "$hotelName".
                Copia este código e introdúcelo en la app:
                
                Código: $token
            """.trimIndent()

                // 5) Lanzar chooser de envío
                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "message/rfc822"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    startActivity(
                        Intent.createChooser(
                            shareIntent,
                            "Enviar invitación vía…"
                        )
                    )
                    dialog.dismiss()
                }
            }
        }
    }


}
