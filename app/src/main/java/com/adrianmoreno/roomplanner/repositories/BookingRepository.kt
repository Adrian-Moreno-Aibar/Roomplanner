package com.adrianmoreno.roomplanner.repositories

import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.adrianmoreno.roomplanner.models.Booking
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BookingRepository {
    private val db = FirebaseFirestore.getInstance()
    private val BOOKINGS = "bookings"
    private val ROOMS    = "rooms"

    /** Marca una habitación como ocupada */
    private suspend fun markRoomOccupied(roomId: String) {
        db.collection(ROOMS).document(roomId)
            .update("status", "OCUPADA")
            .await()
    }

    /** Marca una habitación como libre (cancelación), sin tocar isClean */
    private suspend fun markRoomFree(roomId: String) {
        db.collection(ROOMS).document(roomId)
            .update("status", "LIBRE")
            .await()
    }

    /** Marca una habitación libre y la ensucia (checkout real) */
    suspend fun markRoomFreeAndDirty(roomId: String) {
        db.collection(ROOMS).document(roomId)
            .update(mapOf(
                "status"  to "LIBRE",
                "isClean" to false
            ))
            .await()
    }


    suspend fun sweepCheckout() {
        val now = Timestamp.now()
        val snaps = db.collection(BOOKINGS)
            .whereLessThanOrEqualTo("checkOutDate", now)
            .get()
            .await()

        snaps.documents.forEach { doc ->
            val b = doc.toObject(Booking::class.java) ?: return@forEach
            // 1) marcar habitación sucia
           markRoomFreeAndDirty(b.roomRef)
            // 2) eliminar reserva
            //doc.reference.delete().await()
        }
    }

    /**
     * Recupera reservas que se solapen con el rango dado
     */
    private suspend fun getOverlapping(
        hotelId: String,
        roomId: String,
        checkIn: Timestamp,
        checkOut: Timestamp
    ): List<Booking> {
        // ... tu implementación actual (idéntica) ...
        return try {
            val snaps = db.collection(BOOKINGS)
                .whereEqualTo("hotelRef", hotelId)
                .whereEqualTo("roomRef", roomId)
                .whereLessThan("checkInDate", checkOut)
                .get()
                .await()
            snaps.mapNotNull { it.toObject(Booking::class.java) }
                .filter { it.checkOutDate.toDate().after(checkIn.toDate()) }
        } catch (e: FirebaseFirestoreException) {
            if (e.code == Code.FAILED_PRECONDITION) {
                Log.w("BookingRepo", "Índice faltante, fallback cliente")
                val snaps = db.collection(BOOKINGS)
                    .whereEqualTo("hotelRef", hotelId)
                    .whereEqualTo("roomRef", roomId)
                    .get()
                    .await()
                snaps.mapNotNull { it.toObject(Booking::class.java) }
                    .filter {
                        it.checkInDate.toDate().before(checkOut.toDate()) &&
                                it.checkOutDate.toDate().after(checkIn.toDate())
                    }
            } else {
                Log.e("BookingRepo", "Error comprobando solapamientos", e)
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("BookingRepo", "Error comprobando solapamientos", e)
            emptyList()
        }
    }

    /**
     * Crea la reserva si está libre, y marca la habitación ocupada.
     */
    suspend fun createIfAvailable(b: Booking): String? {
        val conflicts = getOverlapping(b.hotelRef, b.roomRef, b.checkInDate, b.checkOutDate)
        if (conflicts.isNotEmpty()) {
            Log.w("BookingRepo", "Conflicto de fechas: $conflicts")
            return null
        }
        return try {
            val ref    = db.collection(BOOKINGS).document()
            val withId = b.copy(id = ref.id)
            ref.set(withId).await()
            // anotar el rango reservado
            db.collection(ROOMS).document(b.roomRef)
                .update(
                    "reservedRanges",
                    FieldValue.arrayUnion(
                        mapOf("from" to b.checkInDate, "to" to b.checkOutDate)
                    )
                )
                .await()
            // marcar ocupada
            markRoomOccupied(b.roomRef)
            ref.id
        } catch (e: Exception) {
            Log.e("BookingRepo", "Error creando reserva", e)
            null
        }
    }

    /**
     * Actualiza la reserva si sigue libre, mueve la ocupación si cambió de habitación.
     */
    suspend fun updateIfAvailable(b: Booking): Boolean {
        // recupera la versión actual para ver si cambió de room
        val snap    = db.collection(BOOKINGS).document(b.id).get().await()
        val old     = snap.toObject(Booking::class.java)
        val oldRoom = old?.roomRef

        val conflicts = getOverlapping(b.hotelRef, b.roomRef, b.checkInDate, b.checkOutDate)
            .filter { it.id != b.id }
        if (conflicts.isNotEmpty()) {
            Log.w("BookingRepo", "Edición cancelada, conflicto: $conflicts")
            return false
        }
        return try {
            db.collection(BOOKINGS).document(b.id).set(b).await()
            // si cambió de habitación, liberar la antigua sin ensuciar
            if (oldRoom != null && oldRoom != b.roomRef) {
                markRoomFree(oldRoom)
            }
            // ocupar la nueva
            markRoomOccupied(b.roomRef)
            true
        } catch (e: Exception) {
            Log.e("BookingRepo", "Error actualizando reserva", e)
            false
        }
    }

    /**
     * “Cancelación” de reserva desde la UI:
     * - elimina el documento
     * - quita el rango de reservedRanges
     * - libera la habitación **sin** ensuciarla
     */
    suspend fun cancelReservation(id: String): Boolean {
        return try {
            val snap = db.collection(BOOKINGS).document(id).get().await()
            val b    = snap.toObject(Booking::class.java)
                ?: return false

            // 1) borrar reserva
            db.collection(BOOKINGS).document(id).delete().await()

            // 2) eliminar rango reservado
            db.collection(ROOMS).document(b.roomRef)
                .update(
                    "reservedRanges",
                    FieldValue.arrayRemove(
                        mapOf("from" to b.checkInDate, "to" to b.checkOutDate)
                    )
                )
                .await()

            // 3) liberar sin ensuciar
            markRoomFree(b.roomRef)
            true
        } catch (e: Exception) {
            Log.e("BookingRepo", "Error cancelando reserva", e)
            false
        }
    }

    /**
     * Borrado “puro” de reserva (p. ej. mantenimiento), quita también el rango y deja estado
     * de room a libre (pero sin tocar isClean ni reservedRanges adicionales).
     */
    suspend fun delete(id: String): Boolean {
        return try {
            val snap = db.collection(BOOKINGS).document(id).get().await()
            val b    = snap.toObject(Booking::class.java) ?: return false
            db.collection(BOOKINGS).document(id).delete().await()
            db.collection(ROOMS).document(b.roomRef)
                .update(
                    "reservedRanges",
                    FieldValue.arrayRemove(
                        mapOf("from" to b.checkInDate, "to" to b.checkOutDate)
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            Log.e("BookingRepo", "Error borrando reserva", e)
            false
        }
    }

    /**
     * Lee próximas reservas (sin cambios).
     */
    fun getUpcoming(
        hotelId: String,
        from: Timestamp,
        to: Timestamp,
        callback: (List<Booking>) -> Unit
    ) {
        db.collection(BOOKINGS)
            .whereEqualTo("hotelRef", hotelId)
            .whereGreaterThanOrEqualTo("checkInDate", from)
            .whereLessThanOrEqualTo("checkInDate", to)
            .orderBy("checkInDate")
            .get()
            .addOnSuccessListener { snaps ->
                callback(snaps.mapNotNull { it.toObject(Booking::class.java) })
            }
            .addOnFailureListener { e ->
                Log.e("BookingRepo", "Error leyendo reservas", e)
                callback(emptyList())
            }
    }
}
