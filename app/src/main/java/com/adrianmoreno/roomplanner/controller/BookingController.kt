
package com.adrianmoreno.roomplanner.controller

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adrianmoreno.roomplanner.models.Booking
import com.adrianmoreno.roomplanner.repositories.BookingRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

class BookingController(
    private val repo: BookingRepository = BookingRepository()
) : ViewModel() {

    private val _upcoming = MutableLiveData<List<Booking>>()
    val upcoming: LiveData<List<Booking>> = _upcoming

    /** Carga reservas próximas entre dos fechas */
    fun loadUpcoming(hotelId: String, from: Timestamp, to: Timestamp) {
        repo.getUpcoming(hotelId, from, to) { list ->
            _upcoming.postValue(list)
        }
    }

    /** Crea y recarga */
    fun addBooking(b: Booking, hotelId: String, from: Timestamp, to: Timestamp) {
        viewModelScope.launch {
            repo.createIfAvailable(b)
            loadUpcoming(hotelId, from, to)
        }
    }

    /** Edita y recarga */
    fun editBooking(b: Booking, hotelId: String, from: Timestamp, to: Timestamp) {
        viewModelScope.launch {
            repo.updateIfAvailable(b)
            loadUpcoming(hotelId, from, to)
        }
    }

    /** Borra y recarga */
    fun removeBooking(id: String, hotelId: String, from: Timestamp, to: Timestamp) {
        viewModelScope.launch {
            repo.delete(id)
            loadUpcoming(hotelId, from, to)
        }
    }
}
