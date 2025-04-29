package com.adrianmoreno.roomplanner.repositories

import android.util.Log
import com.adrianmoreno.roomplanner.models.Hotel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class HotelRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val COLLECTION = "hotels"

    // 1) CREATE
    suspend fun createHotel(hotel: Hotel): Boolean = try {
        val hotelRef = db.collection(COLLECTION).document()
        val hotelWithId = hotel.copy(id = hotelRef.id)
        hotelRef.set(hotelWithId).await()

        // Asignar el hotel al ADMIN que lo creó
        val userRef = db.collection("users").document(auth.currentUser!!.uid)
        userRef.update("hotelRefs", com.google.firebase.firestore.FieldValue.arrayUnion(hotelRef.id)).await()

        true
    } catch (e: Exception) {
        Log.e("HotelRepo", "Error creando hotel", e)
        false
    }

    // 2) READ ALL — para SUPERADMIN
    fun getAllHotels(callback: (List<Hotel>) -> Unit) {
        db.collection(COLLECTION)
            .get()
            .addOnSuccessListener { result ->
                val list = result.mapNotNull { it.toObject(Hotel::class.java) }
                callback(list)
            }
            .addOnFailureListener { e ->
                Log.e("HotelRepo", "Error listando todos los hoteles", e)
                callback(emptyList())
            }
    }

    // 3) READ FOR USER — para ADMIN/CLEANER
    fun getHotelsForUser(hotelIds: List<String>, callback: (List<Hotel>) -> Unit) {
        if (hotelIds.isEmpty()) {
            callback(emptyList())
            return
        }
        // Firestore only allows up to 10 values in whereIn; para más habría que fragmentar
        db.collection(COLLECTION)
            .whereIn(FieldPath.documentId(), hotelIds)
            .get()
            .addOnSuccessListener { result ->
                val list = result.mapNotNull { it.toObject(Hotel::class.java) }
                callback(list)
            }
            .addOnFailureListener { e ->
                Log.e("HotelRepo", "Error listando hoteles de usuario", e)
                callback(emptyList())
            }
    }

    // 4) UPDATE
    suspend fun updateHotel(hotel: Hotel): Boolean = try {
        db.collection(COLLECTION).document(hotel.id)
            .set(hotel)
            .await()
        true
    } catch (e: Exception) {
        Log.e("HotelRepo", "Error actualizando hotel", e)
        false
    }

    // 5) DELETE
    suspend fun deleteHotel(hotelId: String): Boolean = try {
        db.collection(COLLECTION).document(hotelId)
            .delete()
            .await()
        true
    } catch (e: Exception) {
        Log.e("HotelRepo", "Error eliminando hotel", e)
        false
    }
}
