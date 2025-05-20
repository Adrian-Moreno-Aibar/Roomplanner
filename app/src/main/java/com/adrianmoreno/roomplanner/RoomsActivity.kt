package com.adrianmoreno.roomplanner

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adrianmoreno.roomplanner.adapter.RoomAdapter
import com.adrianmoreno.roomplanner.controller.RoomController
import com.adrianmoreno.roomplanner.models.Room
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class RoomsActivity : AppCompatActivity() {

    private val controller = RoomController()
    private lateinit var adapter: RoomAdapter
    private lateinit var hotelId: String
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rooms)

        hotelId = intent.getStringExtra("HOTEL_ID") ?: return

        val recycler = findViewById<RecyclerView>(R.id.roomsRecyclerView)
        val fabAdd   = findViewById<ImageView>(R.id.fabAddRoom)

        recycler.layoutManager = GridLayoutManager(this, 2)
        adapter = RoomAdapter(
            onToggleClean = { roomId, newClean ->
                // Actualiza isClean y recarga
                db.collection("rooms").document(roomId)
                    .update("isClean", newClean)
                    .addOnSuccessListener {
                        controller.loadRooms(hotelId)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error actualizando limpieza", Toast.LENGTH_SHORT).show()
                    }
            },
            onEdit = { room ->
                showEditDialog(room)
            },
            onDelete = { roomId ->
                lifecycleScope.launch {
                    controller.deleteRoom(roomId, hotelId)
                }
            }
        )
        recycler.adapter = adapter

        controller.rooms.observe(this) { list ->
            adapter.submitList(list)
        }

        // Carga inicial y disparador de liberación/ocupación automática
        lifecycleScope.launch {
            controller.syncRoomStatuses(hotelId)
            controller.loadRooms(hotelId)
        }

        fabAdd.setOnClickListener { showAddDialog() }
    }

    private fun showAddDialog() {
        val v = LayoutInflater.from(this)
            .inflate(R.layout.dialog_new_room, null)
        val etNumber = v.findViewById<EditText>(R.id.editRoomNumber)

        AlertDialog.Builder(this)
            .setTitle("Añadir Habitación")
            .setView(v)
            .setPositiveButton("Añadir") { _, _ ->
                val num = "Hab. "+etNumber.text.toString().trim()
                if (num.isNotEmpty()) {
                    lifecycleScope.launch {
                        controller.addRoom(
                            Room(id = "", hotelRef = hotelId, number = num,
                                status = "LIBRE", isClean = true, reservedRanges = emptyList()),
                            hotelId
                        )
                    }
                } else {
                    Toast.makeText(this, "Número requerido", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditDialog(room: Room) {
        val v = LayoutInflater.from(this)
            .inflate(R.layout.dialog_new_room, null)
        val etNumber = v.findViewById<EditText>(R.id.editRoomNumber)
        etNumber.setText(room.number)

        AlertDialog.Builder(this)
            .setTitle("Editar Habitación")
            .setView(v)
            .setPositiveButton("Guardar") { _, _ ->
                val updated = room.copy(number = etNumber.text.toString().trim())
                lifecycleScope.launch {
                    controller.updateRoom(updated, hotelId)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
