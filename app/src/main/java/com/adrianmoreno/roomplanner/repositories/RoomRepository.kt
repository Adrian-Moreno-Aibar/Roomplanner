package com.adrianmoreno.roomplanner.repositories

import android.util.Log
import com.adrianmoreno.roomplanner.models.Room
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class RoomRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val COLLECTION = "rooms"

    // Crear una nueva habitación
    suspend fun createRoom(room: Room): Boolean = try {
        val roomRef = db.collection(COLLECTION).document()
        val roomWithId = room.copy(id = roomRef.id)
        roomRef.set(roomWithId).await()
        true
    } catch (e: Exception) {
        Log.e("RoomRepo", "Error creando habitación", e)
        false
    }

    // Obtener habitaciones de un hotel
    fun getRoomsForHotel(hotelId: String, callback: (List<Room>) -> Unit) {
        db.collection("rooms")
            .whereEqualTo("hotelRef", hotelId)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    callback(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.mapNotNull { it.toObject(Room::class.java) } ?: emptyList()
                callback(list)
            }
    }


    // Actualizar el estado de una habitación
    suspend fun updateRoomStatus(roomId: String, newStatus: String): Boolean = try {
        db.collection(COLLECTION).document(roomId)
            .update("status", newStatus)
            .await()
        true
    } catch (e: Exception) {
        Log.e("RoomRepo", "Error actualizando estado", e)
        false
    }

    // Eliminar una habitación
    suspend fun deleteRoom(roomId: String): Boolean = try {
        db.collection(COLLECTION).document(roomId)
            .delete()
            .await()
        true
    } catch (e: Exception) {
        Log.e("RoomRepo", "Error eliminando habitación", e)
        false
    }

    // Actualizar usuario
    suspend fun updateRoom(room: Room): Boolean = try {
        db.collection(COLLECTION).document(room.id)
            .set(room)
            .await()
        true
    } catch (e: Exception) {
        Log.e("RoomRepo", "Error actualizando habitación", e)
        false
    }
}
