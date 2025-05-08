package com.adrianmoreno.roomplanner.controller

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adrianmoreno.roomplanner.models.Hotel
import com.adrianmoreno.roomplanner.models.User
import com.adrianmoreno.roomplanner.repositories.HotelRepository
import kotlinx.coroutines.launch

class HotelController(
    val repo: HotelRepository = HotelRepository()
) : ViewModel() {

    private val _hotels = MutableLiveData<List<Hotel>>()
    val hotels: LiveData<List<Hotel>> = _hotels

    /** Para Superadmin */
    fun loadAllHotels() {
        repo.getAllHotels { list ->
            _hotels.postValue(list)
        }
    }

    /** Para Admin/Cleaner */
    fun loadHotelsForUser(user: User) {
        repo.getHotelsForUser(user.hotelRefs) { list ->
            _hotels.postValue(list)
        }
    }

    /** Crea un hotel (no recarga) */
    fun addHotel(hotel: Hotel) {
        viewModelScope.launch {
            repo.createHotel(hotel)
            // no recarga automática
        }
    }

    /** Actualiza un hotel (no recarga) */
    fun updateHotel(hotel: Hotel) {
        viewModelScope.launch {
            repo.updateHotel(hotel)
            // no recarga automática
        }
    }

    /** Borra un hotel simple (no recarga) */
    fun deleteHotel(hotelId: String) {
        viewModelScope.launch {
            repo.deleteHotel(hotelId)
            // no recarga automática
        }
    }

    /** Borra un hotel y sus habitaciones (no recarga) */
    fun deleteHotelCascade(hotelId: String) {
        viewModelScope.launch {
            repo.deleteHotelCascade(hotelId)
            // no recarga automática
        }
    }
}
