// RoomController.kt
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
): ViewModel() {

    private val _rooms = MutableLiveData<List<Room>>()
    val rooms: LiveData<List<Room>> = _rooms

    /** Inicializa la escucha en tiempo real para un hotel dado */
    fun observeRooms(hotelId: String) {
        repo.getRoomsForHotel(hotelId) { list ->
            _rooms.postValue(list)
        }
    }

    fun addRoom(room: Room) {
        viewModelScope.launch {
            if (repo.createRoom(room)) {
                observeRooms(room.hotelRef)
            }
        }
    }

    fun updateRoom(room: Room) {
        viewModelScope.launch {
            if (repo.updateRoom(room)) {
                observeRooms(room.hotelRef)
            }
        }
    }

    fun updateRoomStatus(roomId: String, newStatus: String) {
        viewModelScope.launch {
            if (repo.updateRoomStatus(roomId, newStatus)) {
                // Re-observe si ya tienes el hotelId guardado, o
                // simplemente relies on snapshot listener para refrescar
            }
        }
    }

    fun deleteRoom(room: Room) {
        viewModelScope.launch {
            if (repo.deleteRoom(room.id)) {
                observeRooms(room.hotelRef)
            }
        }
    }
}
