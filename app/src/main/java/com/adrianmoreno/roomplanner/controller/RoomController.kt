// com/adrianmoreno.roomplanner.controller.RoomController.kt
package com.adrianmoreno.roomplanner.controller

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adrianmoreno.roomplanner.models.Room
import com.adrianmoreno.roomplanner.repositories.RoomRepository
import kotlinx.coroutines.launch

class RoomController(
    private val repo: RoomRepository = RoomRepository()
) : ViewModel() {

    private val _rooms = MutableLiveData<List<Room>>()
    val rooms: LiveData<List<Room>> = _rooms

    /** Cargar habitaciones para un hotel específico */
    fun loadRoomsForHotel(hotelId: String) {
        repo.getRoomsForHotel(hotelId) { list ->
            _rooms.postValue(list)
        }
    }

    /** Crear una nueva habitación */
    fun addRoom(room: Room, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repo.createRoom(room)
            onComplete(result != null)
            if (result != null) {
                loadRoomsForHotel(room.hotelRef)
            }
        }
    }

    /** Actualizar el estado de una habitación */
    fun updateStatus(roomId: String, newStatus: String, hotelId: String) {
        viewModelScope.launch {
            val ok = repo.updateRoomStatus(roomId, newStatus)
            if (ok) loadRoomsForHotel(hotelId)
        }
    }
}
