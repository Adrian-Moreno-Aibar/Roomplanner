/**
 * InvitationRepository
 * ====================
 *
 * Gestiona la creación, lectura y actualización de invitaciones para cleaners
 * a un hotel. Utiliza Firestore para almacenar tokens de invitación con fecha
 * de expiración y marca de uso.
 *
 * Responsabilidades principales:
 * - Generar tokens únicos y persistir invitaciones con TTL (time-to-live).
 * - Recuperar invitaciones por token (tanto con callback como suspend).
 * - Marcar invitaciones como usadas.
 */

package com.adrianmoreno.roomplanner.repositories

import android.util.Log
import com.adrianmoreno.roomplanner.models.Invitation
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

class InvitationRepository {
    private val db = FirebaseFirestore.getInstance()
    private val COL = "invitations"

    /**
     * Crea una nueva invitación y devuelve el token generado.
     */
    suspend fun createInvitation(
        email: String,
        name: String,
        hotelId: String,
        ttlDays: Int = 1
    ): String? {
        return try {
            // 1) Generar token aleatorio (8 caracteres)
            val token = UUID.randomUUID().toString().take(8)
            // 2) Construir objeto Invitation con timestamps
            val now = Timestamp.now()
            val inv = Invitation(
                token = token,
                email = email,
                name = name,
                hotelId = hotelId,
                createdAt = now,
                expiresAt = Timestamp(
                    now.seconds + ttlDays * 24 * 3600,
                    0
                ),
                used = false
            )
            // 3) Guardar en Firestore usando el token como ID de documento
            db.collection(COL)
                .document(token)
                .set(inv)
                .await()
            // 4) Devolver token
            token
        } catch (e: Exception) {
            Log.e("InvitationRepo", "Error creando invitación", e)
            null
        }
    }


    /**
     * Recupera una invitación por token.
     */
    suspend fun getInvitationSuspend(token: String): Invitation? =
        try {
            db.collection(COL)
                .document(token)
                .get()
                .await()
                .toObject(Invitation::class.java)
        } catch (e: Exception) {
            Log.e("InvitationRepo", "Error leyendo invitación (suspend)", e)
            null
        }

    /**
     * Marca la invitación como usada: actualiza 'used' a true y añade 'usedAt' con
     * timestamp del servidor.
     * @return true si se actualizó correctamente.
     */
    suspend fun markUsed(token: String): Boolean =
        try {
            db.collection(COL)
                .document(token)
                .update(
                    // 'used' = true y 'usedAt' = timestamp del servidor
                    mapOf(
                        "used" to true,
                        "usedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            Log.e("InvitationRepo", "Error marcando invitación como usada", e)
            false
        }
}
