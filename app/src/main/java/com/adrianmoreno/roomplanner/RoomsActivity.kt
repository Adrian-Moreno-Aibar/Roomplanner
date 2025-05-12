package com.adrianmoreno.roomplanner

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adrianmoreno.roomplanner.R
import com.adrianmoreno.roomplanner.adapter.RoomAdapter
import com.adrianmoreno.roomplanner.controller.RoomController
import com.adrianmoreno.roomplanner.models.Room
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class RoomsActivity : AppCompatActivity() {

    private val controller = RoomController()
    private lateinit var adapter: RoomAdapter
    private lateinit var hotelId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rooms)

        // Recogemos el ID del hotel desde el Intent
        hotelId = intent.getStringExtra("HOTEL_ID")!!

        // Referencias UI
        val recycler = findViewById<RecyclerView>(R.id.roomsRecyclerView)
        val fabAdd   = findViewById<ImageView>(R.id.fabAddRoom)

        // Recycler + Adapter
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = RoomAdapter(
            onToggle = { id, next ->
                lifecycleScope.launch {
                    controller.toggleStatus(id, next, hotelId)
                }
            },
            onEdit = { room ->
                showEditDialog(room)
            },
            onDelete = { id ->
                lifecycleScope.launch {
                    controller.deleteRoom(id, hotelId)
                }
            }
        )
        recycler.adapter = adapter

        // Observamos cambios
        controller.rooms.observe(this) { list ->
            adapter.submitList(list)
        }

        // Carga inicial
        controller.loadRooms(hotelId)

        // Añadir habitación
        fabAdd.setOnClickListener {
            showAddDialog()
        }
    }

    private fun showAddDialog() {
        val v = LayoutInflater.from(this)
            .inflate(R.layout.dialogo_crear_habitacion, null)
        val etNumber = v.findViewById<EditText>(R.id.editRoomNumber)

        AlertDialog.Builder(this)
            .setTitle("Añadir Habitación")
            .setView(v)
            .setPositiveButton("Añadir") { _, _ ->
                val num = etNumber.text.toString().trim()
                if (num.isNotEmpty()) {
                    lifecycleScope.launch {
                        controller.addRoom(
                            Room(id="", number=num, status="LIBRE", hotelRef=hotelId),
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
            .inflate(R.layout.dialogo_crear_habitacion, null)
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
