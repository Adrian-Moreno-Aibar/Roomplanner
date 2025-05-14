// com/adrianmoreno/roomplanner/controller/RoomController.kt
package com.adrianmoreno.roomplanner.controller

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adrianmoreno.roomplanner.models.Room
import com.adrianmoreno.roomplanner.repositories.RoomRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RoomController(
    private val repo: RoomRepository = RoomRepository(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ViewModel() {

    private val _rooms = MutableLiveData<List<Room>>()
    val rooms: LiveData<List<Room>> = _rooms

    /** Carga todas las habitaciones de un hotel */
    fun loadRooms(hotelId: String) {
        repo.getRoomsForHotel(hotelId) { list ->
            _rooms.postValue(list)
        }
    }

    /** Crear habitación y recarga */
    fun addRoom(room: Room, hotelId: String) {
        viewModelScope.launch {
            val newId = repo.createRoom(room)
            if (newId != null) loadRooms(hotelId)
        }
    }

    /** Actualiza habitación entera y recarga */
    fun updateRoom(room: Room, hotelId: String) {
        viewModelScope.launch {
            if (repo.updateRoom(room)) loadRooms(hotelId)
        }
    }

    /** Borra habitación y recarga */
    fun deleteRoom(roomId: String, hotelId: String) {
        viewModelScope.launch {
            if (repo.deleteRoom(roomId)) loadRooms(hotelId)
        }
    }

    /**
     * Sincroniza el campo `status` de cada habitación conforme a la fecha actual y las reservas.
     * Ocupada si hay una reserva activa; libre en caso contrario.
     */
    suspend fun syncRoomStatuses(hotelId: String) {
        // 1) Leer todas las habitaciones de este hotel
        val roomsSnap = db.collection("rooms")
            .whereEqualTo("hotelRef", hotelId)
            .get()
            .await()

        val now = Timestamp.now()

        for (doc in roomsSnap.documents) {
            val room = doc.toObject(Room::class.java) ?: continue
            val roomRef = doc.reference

            // 2) Consulta INDEXABLE Y AUTORIZADA:
            //    - Igualdad hotelRef
            //    - Igualdad roomRef
            //    - Desigualdad checkInDate <= now
            //    - Orden descendente por checkInDate
            val lastBookingSnap = db.collection("bookings")
                .whereEqualTo("hotelRef", hotelId)
                .whereEqualTo("roomRef", room.id)
                .whereLessThanOrEqualTo("checkInDate", now)
                .orderBy("checkInDate", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            // 3) Si existe reserva y aún no terminó → OCUPADA
            val isOccupied = lastBookingSnap.documents
                .mapNotNull { it.toObject(com.adrianmoreno.roomplanner.models.Booking::class.java) }
                .firstOrNull()
                ?.let { booking -> booking.checkOutDate.seconds > now.seconds }
                ?: false

            val desiredStatus = if (isOccupied) "OCUPADA" else "LIBRE"
            if (room.status != desiredStatus) {
                roomRef.update("status", desiredStatus).await()
            }
        }
    }
}
