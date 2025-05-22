package com.adrianmoreno.roomplanner

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.adrianmoreno.roomplanner.repositories.InvitationRepository
import com.adrianmoreno.roomplanner.repositories.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class JoinHotelDialogFragment : DialogFragment() {

    private val invRepo  = InvitationRepository()
    private val userRepo = UserRepository()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_join_hotel, null)
        val etToken = view.findViewById<EditText>(R.id.etToken)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Unirse a un hotel")
            .setView(view)
            .setPositiveButton("Unirse", null)   // lo interceptamos en onStart()
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val token = etToken.text.toString().trim()
                if (token.isEmpty()) {
                    Toast.makeText(context, "Introduce el código", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    // 1) validamos invitación
                    val inv = invRepo.getInvitationSuspend(token)
                    if (inv == null || inv.used ||
                        inv.expiresAt.toDate().before(Date())) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context,
                                "Invitación inválida o caducada", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    // 2) añadimos hotel al usuario actual
                    val ok = userRepo.addHotelToCurrentUser(inv.hotelId)
                    if (!ok) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context,
                                "Error al unirse al hotel", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    // 3) marcamos invitación usada
                    invRepo.markUsed(token)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context,
                            "¡Te has unido al hotel!", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        // opcional: notificar al Dashboard para recargar lista de hoteles
                        (activity as? DashboardActivity)?.reloadHotels()
                    }
                }
            }
        }
        return dialog
    }
}
