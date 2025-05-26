package com.adrianmoreno.roomplanner.repositories

import android.util.Log
import com.adrianmoreno.roomplanner.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val COLLECTION = "users"

    // Referencia al documento del usuario actual
    fun getCurrentUserDoc(): DocumentReference? =
        auth.currentUser?.uid?.let { db.collection(COLLECTION).document(it) }

    // Crear perfil de usuario tras registro
    suspend fun createUserProfile(user: User): Boolean = try {
        getCurrentUserDoc()?.set(user)?.await()
        true
    } catch (e: Exception) {
        Log.e("UserRepo", "Error creando perfil", e)
        false
    }

    // Leer un usuario concreto
    fun getUserById(uid: String, callback: (User?) -> Unit) {
        db.collection(COLLECTION).document(uid)
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.toObject(User::class.java))
            }
            .addOnFailureListener {
                Log.e("UserRepo", "Error obteniendo usuario", it)
                callback(null)
            }
    }

    // (OPCIONAL) Listar todos los usuarios — solo si tus reglas de Firestore lo permiten
    fun getAllUsers(callback: (List<User>) -> Unit) {
        db.collection(COLLECTION).get()
            .addOnSuccessListener { result ->
                val users = result.mapNotNull { it.toObject(User::class.java) }
                callback(users)
            }
            .addOnFailureListener {
                Log.e("UserRepo", "Error listando usuarios", it)
                callback(emptyList())
            }
    }

    // Actualizar usuario
    suspend fun updateUser(user: User): Boolean = try {
        db.collection(COLLECTION).document(user.uid)
            .set(user)
            .await()
        true
    } catch (e: Exception) {
        Log.e("UserRepo", "Error actualizando usuario", e)
        false
    }

    // Eliminar usuario
    suspend fun deleteUser(uid: String): Boolean = try {
        db.collection(COLLECTION).document(uid)
            .delete()
            .await()
        true
    } catch (e: Exception) {
        Log.e("UserRepo", "Error eliminando usuario", e)
        false
    }

    /**
     * Devuelve la lista de usuarios asignados al hotel especificado.
     */
    fun getUsersForHotel(hotelId: String, callback: (List<User>) -> Unit) {
        db.collection(COLLECTION)
            .whereIn("role", listOf("CLEANER","ADMIN"))
            .whereArrayContains("hotelRefs", hotelId)
            .get()
            .addOnSuccessListener { snaps ->
                val users = snaps.mapNotNull { it.toObject(User::class.java) }
                callback(users)
            }
            .addOnFailureListener {
                Log.e("UserRepo", "Error obteniendo los usuarios del hotel", it)
                callback(emptyList())
            }
    }

    suspend fun removeCleanerFromHotel(uid: String, hotelId: String): Boolean = try {
        db.collection("users").document(uid)
            .update("hotelRefs", FieldValue.arrayRemove(hotelId))
            .await()
        true
    } catch(e: Exception) {
        Log.e("UserRepo", "Error quitando hotelRefs", e)
        false
    }

    /** Añade un hotel al array hotelRefs del usuario actual */
    suspend fun addHotelToCurrentUser(hotelId: String): Boolean = try {
        val uid = auth.currentUser!!.uid
        db.collection(COLLECTION).document(uid)
            .update("hotelRefs", FieldValue.arrayUnion(hotelId))
            .await()
        true
    } catch (e: Exception) {
        Log.e("UserRepo", "Error añadiendo hotelRefs", e)
        false
    }
}
