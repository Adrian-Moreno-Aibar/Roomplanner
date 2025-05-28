/**
 * UserController
 * --------------
 * Gestiona la lista de cleaners asignados a un hotel y las operaciones
 * de añadir, eliminar y recargar dicho listado.
 *
 */

package com.adrianmoreno.roomplanner.controller

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adrianmoreno.roomplanner.models.User
import com.adrianmoreno.roomplanner.repositories.UserRepository
import kotlinx.coroutines.launch

class UserController(
    private val repo: UserRepository = UserRepository()
) : ViewModel() {

    private val _cleaners = MutableLiveData<List<User>>()
    val cleaners: LiveData<List<User>> = _cleaners

    /** Carga sólo los CLEANER asignados al hotel */
    fun loadCleanersForHotel(hotelId: String) {
        repo.getUsersForHotel(hotelId) { list ->
            _cleaners.postValue(list)
        }
    }

    /** Elimina la referencia al hotel del cleaner y recarga la lista */
    /** Ahora acepta un onComplete para informar de éxito o fallo */
    fun removeCleanerFromHotel(uid: String, hotelId: String) {
        viewModelScope.launch {
            val ok = repo.removeUserFromHotel(uid, hotelId)
            if (ok) {
                loadCleanersForHotel(hotelId)
            }
            // si quieres notificar al fragment, podrías exponer otro LiveData o SingleLiveEvent
        }
    }

    /** Crea el perfil en Firestore y recarga la lista */
    fun addUser(user: User) {
        viewModelScope.launch {
            val success = repo.createUserProfile(user)
            if (success) {
                // recarga tras crear
                loadCleanersForHotel(user.hotelRefs.first())
            }
        }
    }

    /** Borra un usuario y recarga la lista */
    fun deleteUser(uid: String, hotelId: String) {
        viewModelScope.launch {
            val success = repo.deleteUser(uid)
            if (success) {
                // recarga tras borrar
                loadCleanersForHotel(hotelId)
            }
        }
    }


}
