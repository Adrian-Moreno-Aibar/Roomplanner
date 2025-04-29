// UserController.kt
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
): ViewModel() {

    private val _users = MutableLiveData<List<User>>()
    val users: LiveData<List<User>> = _users

    init {
        loadAllUsers()
    }

    private fun loadAllUsers() {
        repo.getAllUsers { list ->
            _users.postValue(list)
        }
    }

    fun addUser(user: User) {
        viewModelScope.launch {
            if (repo.createUserProfile(user)) {
                loadAllUsers()
            }
        }
    }

    fun updateUser(user: User) {
        viewModelScope.launch {
            if (repo.updateUser(user)) {
                loadAllUsers()
            }
        }
    }

    fun deleteUser(uid: String) {
        viewModelScope.launch {
            if (repo.deleteUser(uid)) {
                loadAllUsers()
            }
        }
    }
}
