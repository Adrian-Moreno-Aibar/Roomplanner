// src/main/java/com/adrianmoreno/roomplanner/RoomsFragment.kt
package com.adrianmoreno.roomplanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adrianmoreno.roomplanner.adapter.RoomAdapter
import com.adrianmoreno.roomplanner.controller.RoomController
import com.adrianmoreno.roomplanner.models.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class RoomsFragment : Fragment() {

    private val controller = RoomController()
    private lateinit var adapter: RoomAdapter
    private lateinit var hotelId: String
    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val ARG_HOTEL_ID = "HOTEL_ID"
        private const val ARG_HOTEL_NAME = "ARG_HOTEL_NAME"
        private const val ARG_USER_ROLE  = "USER_ROLE"

        fun newInstance(hotelId: String, hotelName: String, role: String) = RoomsFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_HOTEL_ID, hotelId)
                putString(ARG_HOTEL_NAME, hotelName)
                putString(ARG_USER_ROLE,  role)
            }
        }
    }
    private lateinit var role: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hotelId = arguments?.getString(ARG_HOTEL_ID)
            ?: throw IllegalArgumentException("RoomsFragment requires a hotelId")
        role    = arguments?.getString(ARG_USER_ROLE)!!
        val hotelName = arguments?.getString(ARG_HOTEL_NAME) ?: ""
        // Si quieres, pon el título del Toolbar aquí:
        activity?.title = hotelName


    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflamos el layout fragment_rooms.xml
        val view = inflater.inflate(R.layout.fragment_rooms, container, false)

        val recycler = view.findViewById<RecyclerView>(R.id.roomsRecyclerView)
        val fabAdd = view.findViewById<Button>(R.id.fabAddRoom)
        // ahora `role` está disponible aquí
        if (role == "CLEANER") {
            fabAdd.visibility = View.GONE
        }


        recycler.layoutManager = GridLayoutManager(context, 2)
        adapter = RoomAdapter(
            onToggleClean = { roomId, newClean ->
                db.collection("rooms").document(roomId)
                    .update("isClean", newClean)
                    .addOnSuccessListener { controller.loadRooms(hotelId) }
                    .addOnFailureListener {
                        Toast.makeText(context, "Error actualizando limpieza", Toast.LENGTH_SHORT).show()
                    }
            },
            onEdit = { room -> showEditDialog(room) },
            canManage     = role != "CLEANER",
            onDelete = { roomId ->
                lifecycleScope.launch { controller.deleteRoom(roomId, hotelId) }
            }
        )
        recycler.adapter = adapter

        controller.rooms.observe(viewLifecycleOwner) { list ->
            // Extraemos sólo los dígitos de `room.number` y convertimos a Int para pasar
            // la lista de habitaciones ordenada
            val sorted = list.sortedBy { room ->
                room.number.filter { it.isDigit() }
                    .toIntOrNull() ?: Int.MAX_VALUE
            }
            adapter.submitList(sorted)
        }


        // Carga inicial + sincronización de estados
        lifecycleScope.launch {
            controller.syncRoomStatuses(hotelId)
            controller.loadRooms(hotelId)
        }

        fabAdd.setOnClickListener { showAddDialog() }

        return view
    }

    private fun showAddDialog() {
        val v = LayoutInflater.from(context)
            .inflate(R.layout.dialog_new_room, null)
        val etNumber = v.findViewById<EditText>(R.id.editRoomNumber)

        AlertDialog.Builder(requireContext())
            .setTitle("Añadir Habitación")
            .setView(v)
            .setPositiveButton("Añadir") { _, _ ->
                val num = "Hab. " + etNumber.text.toString().trim()
                if (num.isNotEmpty()) {
                    lifecycleScope.launch {
                        controller.addRoom(
                            Room(
                                id = "",
                                hotelRef = hotelId,
                                number = num,
                                status = "LIBRE",
                                isClean = true,
                                reservedRanges = emptyList()
                            ),
                            hotelId
                        )
                    }
                } else {
                    Toast.makeText(context, "Número requerido", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditDialog(room: Room) {
        val v = LayoutInflater.from(context)
            .inflate(R.layout.dialog_new_room, null)
        val etNumber = v.findViewById<EditText>(R.id.editRoomNumber)
        etNumber.setText(room.number)

        AlertDialog.Builder(requireContext())
            .setTitle("Editar Habitación")
            .setView(v)
            .setPositiveButton("Guardar") { _, _ ->
                val updated = room.copy(number = etNumber.text.toString().trim())
                lifecycleScope.launch { controller.updateRoom(updated, hotelId) }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
