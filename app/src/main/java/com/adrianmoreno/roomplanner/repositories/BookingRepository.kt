package com.adrianmoreno.roomplanner.repositories

import android.util.Log
import com.adrianmoreno.roomplanner.models.Booking
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlinx.coroutines.tasks.await

class BookingRepository {
    private val db = FirebaseFirestore.getInstance()
    private val COL = "bookings"

    /**
     * Recupera todas las reservas del mismo hotel y habitación,
     * filtra en servidor por checkInDate < checkOut, y si falta índice
     * hace fallback trayendo todas y filtrando ambos extremos en cliente.
     */
    private suspend fun getOverlapping(
        hotelId: String,
        roomId: String,
        checkIn: Timestamp,
        checkOut: Timestamp
    ): List<Booking> {
        return try {
            // Intento consulta con una desigualdad (puede requerir índice compuesto)
            val snaps = db.collection(COL)
                .whereEqualTo("hotelRef", hotelId)
                .whereEqualTo("roomRef", roomId)
                .whereLessThan("checkInDate", checkOut)
                .get()
                .await()

            snaps.mapNotNull { it.toObject(Booking::class.java) }
                .filter { existing ->
                    // segunda desigualdad en cliente
                    existing.checkOutDate.toDate().after(checkIn.toDate())
                }

        } catch (e: FirebaseFirestoreException) {
            // Si falla por falta de índice, fallback total en cliente
            if (e.code == Code.FAILED_PRECONDITION) {
                Log.w("BookingRepo", "Índice faltante, fallback cliente")
                val snaps = db.collection(COL)
                    .whereEqualTo("hotelRef", hotelId)
                    .whereEqualTo("roomRef", roomId)
                    .get()
                    .await()

                snaps.mapNotNull { it.toObject(Booking::class.java) }
                    .filter { existing ->
                        existing.checkInDate.toDate().before(checkOut.toDate()) &&
                                existing.checkOutDate.toDate().after(checkIn.toDate())
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
     * Crea la reserva solo si no hay solapamientos.
     * Además añade el rango en `reservedRanges` de la Room.
     * Devuelve el ID nuevo, o null si hay conflicto o error.
     */
    suspend fun createIfAvailable(b: Booking): String? {
        // 1) validar solapamientos
        val conflicts = getOverlapping(
            hotelId  = b.hotelRef,
            roomId   = b.roomRef,
            checkIn  = b.checkInDate,
            checkOut = b.checkOutDate
        )
        if (conflicts.isNotEmpty()) {
            Log.w("BookingRepo", "Conflicto de fechas: $conflicts")
            return null
        }
        // 2) crear booking
        return try {
            val ref    = db.collection(COL).document()
            val withId = b.copy(id = ref.id)
            ref.set(withId).await()
            // 3) actualizar Room para anotar el rango reservado
            db.collection("rooms").document(b.roomRef)
                .update(
                    "reservedRanges",
                    FieldValue.arrayUnion(
                        mapOf("from" to b.checkInDate, "to" to b.checkOutDate)
                    )
                )
                .await()
            ref.id
        } catch (e: Exception) {
            Log.e("BookingRepo", "Error creando reserva", e)
            null
        }
    }

    /**
     * Actualiza la reserva solo si no hay conflictos (excluyendo ella misma).
     * Devuelve true si se actualizó, false si hay conflicto o error.
     */
    suspend fun updateIfAvailable(b: Booking): Boolean {
        // 1) validar solapamientos excluyendo la propia reserva
        val conflicts = getOverlapping(
            hotelId  = b.hotelRef,
            roomId   = b.roomRef,
            checkIn  = b.checkInDate,
            checkOut = b.checkOutDate
        ).filter { it.id != b.id }

        if (conflicts.isNotEmpty()) {
            Log.w("BookingRepo", "Edición cancelada, conflicto: $conflicts")
            return false
        }
        // 2) aplicar update
        return try {
            db.collection(COL).document(b.id).set(b).await()
            true
        } catch (e: Exception) {
            Log.e("BookingRepo", "Error actualizando reserva", e)
            false
        }
    }

    /**
     * Borra una reserva y remueve el rango de `reservedRanges` de la Room.
     */
    suspend fun delete(id: String): Boolean {
        return try {
            val snap = db.collection(COL).document(id).get().await()
            val b    = snap.toObject(Booking::class.java)
            if (b == null) {
                Log.w("BookingRepo", "Reserva no encontrada al borrar: $id")
                return false
            }
            // 1) borrar reserva
            db.collection(COL).document(id).delete().await()
            // 2) eliminar rango reservado
            db.collection("rooms").document(b.roomRef)
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
     * Lee reservas de un hotel entre dos fechas (para dashboard/listado).
     */
    fun getUpcoming(
        hotelId: String,
        from: Timestamp,
        to: Timestamp,
        callback: (List<Booking>) -> Unit
    ) {
        db.collection(COL)
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
