// HotelController.kt
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
    private val repo: HotelRepository = HotelRepository()
): ViewModel() {

    private val _hotels = MutableLiveData<List<Hotel>>()
    val hotels: LiveData<List<Hotel>> = _hotels

    /** Para Superadmin */
    fun loadAllHotels() {
        repo.getAllHotels { list ->
            _hotels.postValue(list)
        }
    }

    /** Para Admin */
    fun loadHotelsForUser(user: User) {
        repo.getHotelsForUser(user.hotelRefs) { list ->
            _hotels.postValue(list)
        }
    }

    fun addHotel(hotel: Hotel) {
        viewModelScope.launch {
            if (repo.createHotel(hotel)) {
                // recarga tras crear
                _hotels.value?.let { list -> loadAllHotels() }
            }
        }
    }

    fun updateHotel(hotel: Hotel) {
        viewModelScope.launch {
            if (repo.updateHotel(hotel)) {
                loadAllHotels()
            }
        }
    }

    fun deleteHotel(hotelId: String) {
        viewModelScope.launch {
            if (repo.deleteHotel(hotelId)) {
                loadAllHotels()
            }
        }
    }
}
