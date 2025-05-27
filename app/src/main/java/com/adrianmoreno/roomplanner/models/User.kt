package com.adrianmoreno.roomplanner.models

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "",             // "ADMIN" o "CLEANER"
    val hotelRefs: List<String> = emptyList(),
    val photoUrl: String = ""
)
{
    override fun toString(): String {
        return "User(uid='$uid',name='$name',email='$email',role='$role',hotelRefs='$hotelRefs',photoUrl='$photoUrl')"
    }
}