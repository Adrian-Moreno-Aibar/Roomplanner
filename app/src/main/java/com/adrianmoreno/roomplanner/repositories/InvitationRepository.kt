package com.adrianmoreno.roomplanner.repositories

import android.util.Log
import com.adrianmoreno.roomplanner.models.Invitation
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import java.util.*

class InvitationRepository {

    private val db = FirebaseFirestore.getInstance()
    private val COL = "invitations"

    /** Crea una invitación y devuelve el token o null */
    suspend fun createInvitation(
        email: String, name: String, hotelId: String,
        ttlDays: Int = 1
    ): String? {
        return try {
            val token = UUID.randomUUID().toString().take(8)
            val inv = Invitation(
                token     = token,
                email     = email,
                name      = name,
                hotelId   = hotelId,
                createdAt = Timestamp.now(),
                expiresAt = Timestamp(
                    Timestamp.now().seconds + ttlDays * 24 * 3600,
                    0
                ),
                used      = false
            )
            db.collection(COL).document(token).set(inv).await()
            token
        } catch (e: Exception) {
            Log.e("InvitationRepo", "Error creando invitación", e)
            null
        }
    }

    /** Recupera la invitación por token */
    fun getInvitation(token: String, cb: (Invitation?) -> Unit) {
        db.collection(COL).document(token)
            .get()
            .addOnSuccessListener { snap ->
                cb(snap.toObject(Invitation::class.java))
            }
            .addOnFailureListener {
                Log.e("InvitationRepo", "Error leyendo invitación", it)
                cb(null)
            }
    }



    suspend fun getInvitationSuspend(token: String): Invitation? =
        try {
            db.collection(COL).document(token)
                .get().await()
                .toObject(Invitation::class.java)
        } catch(e: Exception) { null }

    suspend fun markUsed(token: String): Boolean =
        try {
            db.collection(COL).document(token)
                .update("used", true, "usedAt", FieldValue.serverTimestamp())
                .await()
            true
        } catch(e: Exception) {
            Log.e("InvitationRepo", "Error marking used", e)
            false
        }
}
