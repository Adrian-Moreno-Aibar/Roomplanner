package com.adrianmoreno.roomplanner.models

class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "",             // "ADMIN" o "CLEANER"
    val hotelRefs: List<String> = emptyList()
)
{
    override fun toString(): String {
        return "User(uid='$uid',name='$name',email='$email',role='$role',hotelRefs='$hotelRefs')"
    }
}