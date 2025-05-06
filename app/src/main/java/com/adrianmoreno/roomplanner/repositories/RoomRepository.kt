// com/adrianmoreno/roomplanner/repositories/RoomRepository.kt
package com.adrianmoreno.roomplanner.repositories

import android.util.Log
import com.adrianmoreno.roomplanner.models.Room
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

class RoomRepository {

    private val db = FirebaseFirestore.getInstance()
    private val COLLECTION = "rooms"

    /** Crear habitación y devolver su nuevo ID */
    suspend fun createRoom(room: Room): String? = try {
        val ref = db.collection(COLLECTION).document()
        val withId = room.copy(id = ref.id)
        // guardamos
        ref.set(withId).await()
        // si necesitas actualizar algo en hotel, aquí podrías hacerlo
        ref.id
    } catch (e: Exception) {
        Log.e("RoomRepository", "Error creando habitación", e)
        null
    }

    /** Obtener habitaciones de un hotel */
    fun getRoomsForHotel(hotelId: String, cb: (List<Room>) -> Unit) {
        db.collection(COLLECTION)
            .whereEqualTo("hotelRef", hotelId)
            .get()
            .addOnSuccessListener { snaps ->
                cb(snaps.mapNotNull { it.toObject(Room::class.java) })
            }
            .addOnFailureListener {
                Log.e("RoomRepo", "Error cargando rooms", it)
                cb(emptyList())
            }
    }

    /** Actualizar sólo el campo status */
    suspend fun updateRoomStatus(roomId: String, newStatus: String): Boolean = try {
        db.collection(COLLECTION).document(roomId)
            .update("status", newStatus)
            .await()
        true
    } catch (e: Exception) {
        Log.e("RoomRepository", "Error actualizando estado", e)
        false
    }
}
