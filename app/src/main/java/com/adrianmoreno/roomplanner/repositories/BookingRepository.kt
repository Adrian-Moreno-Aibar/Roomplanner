/**
 * BookingRepository
 * =================
 *
 * Esta clase se encarga de todas las operaciones relacionadas con las reservas
 *  y la actualización del estado de las habitaciones en Firestore.
 * Utiliza corrutinas para llamadas asíncronas y asegura que cada reserva respete
 * las reglas de disponibilidad y limpieza.
 *
 * Principales responsabilidades:
 * 1. Crear, leer, actualizar y eliminar reservas en la colección 'bookings'.
 * 2. Marcar habitaciones como ocupadas, libres o sucias en la colección 'rooms'.
 * 3. Comprobar solapamientos de fechas antes de crear o actualizar una reserva.
 * 4. Borrar reservas caducadas (checkout) y liberar habitaciones automáticamente.
 */


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

    /** Marca una habitación como libre (cancelación), sin ensuciarla */
    private suspend fun markRoomFree(roomId: String) {
        db.collection(ROOMS).document(roomId)
            .update("status", "LIBRE")
            .await()
    }

    /** Marca una habitación libre y la ensucia (checkout) */
    suspend fun markRoomFreeAndDirty(roomId: String) {
        db.collection(ROOMS).document(roomId)
            .update(mapOf(
                "status"  to "LIBRE",
                "isClean" to false
            ))
            .await()
    }

    /**
     * Función que busca reservas que ya hayan acabado para borrarlas
     * y marcar las habitaciones como libres y sucias
     * Busca y procesa checkout automático de reservas cuya fecha de salida ya pasó.
     */
    suspend fun sweepCheckout(hotelIds: List<String>) {
        val now = Timestamp.now()
        hotelIds.forEach { hotelId ->
            // 1) Sólo miramos reservas de este hotel que ya hayan hecho checkout
            val snaps = db.collection(BOOKINGS)
                .whereEqualTo("hotelRef", hotelId)
                .whereLessThanOrEqualTo("checkOutDate", now)
                .get()
                .await()

            snaps.documents.forEach { doc ->
                val b = doc.toObject(Booking::class.java) ?: return@forEach
                // 2) marcamos la habitación libre + sucia
                markRoomFreeAndDirty(b.roomRef)
                // 3) borramos la reserva
                doc.reference.delete().await()
            }
        }
    }

    /**
     * Recupera reservas que se solapan con un rango de fechas dado.
     * Primero intenta usar un índice optimizado, si falta, hace fallback a filtrado en cliente.
     */
    private suspend fun getOverlapping(
        hotelId: String,
        roomId: String,
        checkIn: Timestamp,
        checkOut: Timestamp
    ): List<Booking> {
        // Consulta optimizada: fecha de entrada menor que salida deseada
        return try {
            val snaps = db.collection(BOOKINGS)
                .whereEqualTo("hotelRef", hotelId)
                .whereEqualTo("roomRef", roomId)
                .whereLessThan("checkInDate", checkOut)
                .get()
                .await()
            // Filtrado de salidas posteriores a la fecha de entrada deseada
            snaps.mapNotNull { it.toObject(Booking::class.java) }
                .filter { it.checkOutDate.toDate().after(checkIn.toDate()) }
        } catch (e: FirebaseFirestoreException) {
            if (e.code == Code.FAILED_PRECONDITION) {
                Log.w("BookingRepo", "Índice faltante, fallback cliente")
                // Fallback: obtiene todos y filtra manualmente
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
     * - libera la habitación sin actualizar isClean
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
     * Borrado “puro” de reserva (ej. mantenimiento), quita también el rango y deja estado
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
