package com.adrianmoreno.roomplanner.repositories

import android.util.Log
import com.adrianmoreno.roomplanner.models.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

class RoomRepository {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val COLLECTION = "rooms"

    /** Lee en tiempo real todas las rooms de un hotel */
    fun getRoomsForHotel(hotelId: String, callback: (List<Room>) -> Unit) {
        db.collection(COLLECTION)
            .whereEqualTo("hotelRef", hotelId)
            .get()
            .addOnSuccessListener { snaps ->
                val lst = snaps.mapNotNull { it.toObject(Room::class.java) }
                callback(lst)
            }
            .addOnFailureListener { e ->
                Log.e("RoomRepo", "Error leyendo habitaciones", e)
                callback(emptyList())
            }
    }

    /** Crea una habitación y la asocia al hotel */
    suspend fun createRoom(room: Room): String? = try {
        val ref = db.collection(COLLECTION).document()
        val withId = room.copy(id = ref.id)
        ref.set(withId).await()
        ref.id
    } catch(e: Exception) {
        Log.e("RoomRepo", "Error creando habitación", e)
        null
    }

    /** Actualiza todos los campos de una habitación */
    suspend fun updateRoom(room: Room): Boolean = try {
        db.collection(COLLECTION)
            .document(room.id)
            .set(room)
            .await()
        true
    } catch(e: Exception) {
        Log.e("RoomRepo", "Error actualizando habitación", e)
        false
    }

    /** Borra una habitación concreta */
    suspend fun deleteRoom(roomId: String): Boolean = try {
        db.collection(COLLECTION)
            .document(roomId)
            .delete()
            .await()
        true
    } catch(e: Exception) {
        Log.e("RoomRepo", "Error borrando habitación", e)
        false
    }

    /** Cambia solo el campo `status` de una habitación */
    suspend fun updateRoomStatus(roomId: String, status: String): Boolean = try {
        db.collection(COLLECTION)
            .document(roomId)
            .update("status", status)
            .await()
        true
    } catch(e: Exception) {
        Log.e("RoomRepo", "Error cambiando status", e)
        false
    }
}
