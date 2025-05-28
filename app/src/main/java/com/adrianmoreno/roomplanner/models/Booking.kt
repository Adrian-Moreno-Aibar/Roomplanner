package com.adrianmoreno.roomplanner.models

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

@Parcelize
data class Booking(
    val id: String         = "",
    val hotelRef: String   = "",
    val roomRef: String    = "",
    val guestName: String  = "",
    // @Parcelize generalmente puede manejar Timestamp porque es Serializable
    val checkInDate: Timestamp = Timestamp.now(),
    val checkOutDate: Timestamp = Timestamp.now(),
    val status: String     = "reservado",
    val observations: String = ""
) : Parcelable
