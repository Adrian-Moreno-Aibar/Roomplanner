package com.adrianmoreno.roomplanner

import android.os.Bundle
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
import com.adrianmoreno.roomplanner.models.Room
import com.adrianmoreno.roomplanner.repositories.RoomRepository
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class RoomsActivity : AppCompatActivity() {

    private val hotelId by lazy { intent.getStringExtra("HOTEL_ID")!! }
    private val repo    by lazy { RoomRepository() }

    // 1) Elevamos adapter a propiedad de clase:
    private lateinit var adapter: RoomAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rooms)

        val recycler = findViewById<RecyclerView>(R.id.roomsRecyclerView)
        val fabAdd   = findViewById<ImageView>(R.id.fabAddRoom)

        recycler.layoutManager = LinearLayoutManager(this)

        // 2) Inicializamos el adapter aquí
        adapter = RoomAdapter { roomId, oldStatus ->
            // cuando pulsan "Cambiar estado"
            val nuevo = when (oldStatus) {
                "DISPONIBLE"   -> "OCUPADA"
                "OCUPADA" -> "SUCIA"
                else       -> "DISPONIBLE"
            }
            lifecycleScope.launch {
                val ok = repo.updateRoomStatus(roomId, nuevo)
                // Toast ya corre en el thread principal por defecto:
                Toast.makeText(
                    this@RoomsActivity,
                    if (ok) "Estado actualizado" else "Error actualizando",
                    Toast.LENGTH_SHORT
                ).show()
                // 3) Ahora sí vemos adapter
                loadRooms()
            }
        }

        recycler.adapter = adapter

        // carga inicial
        loadRooms()

        // FAB para crear habitación
        fabAdd.setOnClickListener {
            showAddRoomDialog { number ->
                val r = Room(
                    id       = "",
                    hotelRef = hotelId,
                    number   = number,
                    status   = "DISPONIBLE"
                )
                lifecycleScope.launch {
                    val newId = repo.createRoom(r)
                    Toast.makeText(
                        this@RoomsActivity,
                        if (newId != null) "Hab creada" else "Error creando habitación",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (newId != null) loadRooms()
                }
            }
        }
    }

    // Función reutilizable para recargar lista
    private fun loadRooms() {
        repo.getRoomsForHotel(hotelId) { list ->
            adapter.submitList(list)
        }
    }

    private fun showAddRoomDialog(onAdd: (String) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialogo_crear_habitacion, null)
        val etNumber = dialogView.findViewById<EditText>(R.id.editRoomNumber)

        AlertDialog.Builder(this)
            .setTitle("Añadir Habitación")
            .setView(dialogView)
            .setPositiveButton("Añadir") { _, _ ->
                val num = etNumber.text.toString().trim()
                if (num.isNotEmpty()) onAdd(num)
                else Toast.makeText(this, "Introduce un número", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
