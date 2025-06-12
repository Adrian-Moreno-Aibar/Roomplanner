/**
 * HotelRepository
 * ===============
 *
 * Gestiona todas las operaciones CRUD relacionadas con los hoteles en Firestore.
 * Además, sincroniza la referencia de los hoteles en el perfil del usuario autenticado.
 * Utiliza FirebaseAuth para identificar al usuario actual y Firestore para persistencia.
 *
 * Funcionalidades:
 * - createHotel: crea un nuevo hotel y añade su ID al array `hotelRefs` del usuario.
 * - getAllHotels: obtiene todos los hoteles (para SUPERADMIN).
 * - getHotelsForUser: obtiene solo los hoteles a los que el usuario tiene acceso.
 * - updateHotel: actualiza los datos de un hotel.
 * - deleteHotel: elimina un hotel por ID.
 * - deleteHotelCascade: elimina un hotel y sus habitaciones asociadas, y lo quita de `hotelRefs` del usuario.
 */

package com.adrianmoreno.roomplanner.repositories

import android.util.Log
import com.adrianmoreno.roomplanner.models.Hotel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await


class HotelRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()
    private val COLLECTION = "hotels"

    /**
     * Crea un nuevo hotel y añade su ID al array `hotelRefs` del usuario creador.
     * Devuelve el ID del hotel creado, o `null` en caso de error.
     */
    suspend fun createHotel(hotel: Hotel): String? = try {
        // 1) Referencia a un nuevo documento con ID auto
        val hotelRef = db.collection(COLLECTION).document()
        // 2) Copiamos el objeto incluyendo el ID generado
        val hotelWithId = hotel.copy(id = hotelRef.id)
        // 3) Ejecutamos un batch: insert hotel + actualizar hotelRefs del usuario
        val batch = db.batch().apply {
            set(hotelRef, hotelWithId)
            val userRef = db.collection("users").document(auth.currentUser!!.uid)
            update(userRef, "hotelRefs", FieldValue.arrayUnion(hotelRef.id))
        }
        batch.commit().await()
        hotelRef.id
    } catch (e: Exception) {
        Log.e("HotelRepository", "Error creando hotel", e)
        null
    }

    /**
     * Obtiene todos los hoteles registrados (para SUPERADMIN).
     */
    fun getAllHotels(callback: (List<Hotel>) -> Unit) {
        db.collection(COLLECTION)
            .get()
            .addOnSuccessListener { snaps ->
                val list = snaps.mapNotNull { it.toObject(Hotel::class.java) }
                callback(list)
            }
            .addOnFailureListener { e ->
                Log.e("HotelRepository", "Error obteniendo todos los hoteles", e)
                callback(emptyList())
            }
    }

    /**
     * Obtiene solo los hoteles cuyo ID está en `hotelIds` (para ADMIN / CLEANER).
     */
    fun getHotelsForUser(hotelIds: List<String>, callback: (List<Hotel>) -> Unit) {
        if (hotelIds.isEmpty()) {
            callback(emptyList())
            return
        }
        db.collection(COLLECTION)
            .whereIn(FieldPath.documentId(), hotelIds)
            .get()
            .addOnSuccessListener { snaps ->
                val list = snaps.mapNotNull { it.toObject(Hotel::class.java) }
                callback(list)
            }
            .addOnFailureListener { e ->
                Log.e("HotelRepository", "Error obteniendo hoteles para usuario", e)
                callback(emptyList())
            }
    }
    // UPDATE
    suspend fun updateHotel(hotel: Hotel): Boolean = try {
        db.collection(COLLECTION).document(hotel.id)
            .set(hotel)
            .await()
        true
    } catch (e: Exception) {
        Log.e("HotelRepo", "Error actualizando hotel", e)
        false
    }

    //DELETE
    suspend fun deleteHotel(hotelId: String): Boolean = try {
        db.collection(COLLECTION).document(hotelId)
            .delete()
            .await()
        true
    } catch (e: Exception) {
        Log.e("HotelRepo", "Error eliminando hotel", e)
        false
    }

    suspend fun deleteHotelCascade(hotelId: String): Boolean = try {
        val batch = db.batch()

        // 1) Borrar todas las rooms de ese hotel
        val roomsSnap = db.collection("rooms")
            .whereEqualTo("hotelRef", hotelId)
            .get()
            .await()
        for (doc in roomsSnap.documents) {
            batch.delete(doc.reference)
        }

        // 2) Borrar el hotel
        val hotelRef = db.collection(COLLECTION).document(hotelId)
        batch.delete(hotelRef)

        // 3) Quitar de hotelRefs del admin
        val userRef = db.collection("users").document(auth.currentUser!!.uid)
        batch.update(userRef, "hotelRefs", FieldValue.arrayRemove(hotelId))

        // 4) Ejecutar
        batch.commit().await()
        true
    } catch(e: Exception) {
        Log.e("HotelRepository", "Error borrando en cascada", e)
        false
    }
}

