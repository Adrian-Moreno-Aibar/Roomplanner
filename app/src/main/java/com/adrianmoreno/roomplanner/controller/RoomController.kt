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

    /** Carga todas las habitaciones de un hotel */
    fun loadRooms(hotelId: String) {
        repo.getRoomsForHotel(hotelId) { list ->
            _rooms.postValue(list)
        }
    }

    /** Crea una habitación y recarga */
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

    /** Cambia solo status y recarga */
    fun toggleStatus(roomId: String, newStatus: String, hotelId: String) {
        viewModelScope.launch {
            if (repo.updateRoomStatus(roomId, newStatus)) loadRooms(hotelId)
        }
    }

    /** Borra habitación y recarga */
    fun deleteRoom(roomId: String, hotelId: String) {
        viewModelScope.launch {
            if (repo.deleteRoom(roomId)) loadRooms(hotelId)
        }
    }
}
