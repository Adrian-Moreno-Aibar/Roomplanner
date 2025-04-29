package com.adrianmoreno.roomplanner.repositories

import android.util.Log
import com.adrianmoreno.roomplanner.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val COLLECTION = "users"

    // Referencia al documento del usuario actual
    fun getCurrentUserDoc(): DocumentReference =
        db.collection(COLLECTION).document(auth.currentUser!!.uid)

    // Crear perfil de usuario tras registro
    suspend fun createUserProfile(user: User): Boolean = try {
        getCurrentUserDoc().set(user).await()
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

    // Listar todos los usuarios
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
}
