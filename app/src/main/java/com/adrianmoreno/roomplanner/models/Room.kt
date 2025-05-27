// com/adrianmoreno/roomplanner/models/Room.kt
package com.adrianmoreno.roomplanner.models
import com.google.firebase.firestore.PropertyName

data class Room(
    val id: String = "",
    val hotelRef: String = "",
    val number: String = "",
    val status: String = "",        // "OCUPADA" / "LIBRE"
    val category: String = "",      // “Individual”, “Doble”, “Suite”, …
    val pricePerNight: Double = 0.0, // tarifa en euros

    @get:PropertyName("isClean")
    @set:PropertyName("isClean")
    var isClean: Boolean = true,    // true = limpia, false = sucia

    val reservedRanges: List<ReservedRange> = emptyList()
) {
    override fun toString(): String {
        return "Room(id='$id', hotelRef='$hotelRef', number='$number', status='$status', isClean=$isClean, reservedRanges=$reservedRanges,category='$category', pricePerNight='$pricePerNight')"
    }
}
