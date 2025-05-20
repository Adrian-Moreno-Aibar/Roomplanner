package com.adrianmoreno.roomplanner.models

import com.google.firebase.Timestamp

data class Invitation(
    val token:     String    = "",
    val email:     String    = "",
    val name:      String    = "",
    val hotelId:   String    = "",
    val createdAt: Timestamp = Timestamp.now(),
    val expiresAt: Timestamp = Timestamp.now(),
    val used:      Boolean   = false
)
