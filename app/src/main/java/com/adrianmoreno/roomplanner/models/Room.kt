// com/adrianmoreno/roomplanner/models/Room.kt
package com.adrianmoreno.roomplanner.models

data class Room(
    val id: String = "",
    val hotelRef: String = "",
    val number: String = "",
    val status: String = "",            // "OCCUPIED", "VACANT", etc.
    val reservedRanges: List<ReservedRange> = emptyList()
) {
    override fun toString(): String =
        "Room(id='$id', hotelRef='$hotelRef', number='$number', status='$status', reservedRanges=$reservedRanges)"
}
