// com/adrianmoreno/roomplanner/controller/HotelController.kt
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
) : ViewModel() {

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

    /**
     * Añade un hotel: createHotel devuelve el nuevo ID o null en caso de error.
     * Tras crearlo, recarga la lista.
     */
    fun addHotel(hotel: Hotel) {
        viewModelScope.launch {
            val newId = repo.createHotel(hotel)
            if (newId != null) {
                // si creación OK, recarga todos (o filtra según rol)
                loadAllHotels()
            }
        }
    }

    fun updateHotel(hotel: Hotel) {
        viewModelScope.launch {
            val ok = repo.updateHotel(hotel)
            if (ok) {
                loadAllHotels()
            }
        }
    }

    fun deleteHotel(hotelId: String) {
        viewModelScope.launch {
            val ok = repo.deleteHotel(hotelId)
            if (ok) {
                loadAllHotels()
            }
        }
    }
}
