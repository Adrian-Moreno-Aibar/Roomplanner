/**
 * RoomsFragment
 * =============
 *
 * Fragmento que muestra una cuadrícula de habitaciones para un hotel específico.
 * Permite filtrar por habitaciones sucias, crear, editar, borrar e indicar si están limpias o sucias.
 * Utiliza RoomController para la lógica de negocio y LiveData para actualizar la UI.
 */

package com.adrianmoreno.roomplanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adrianmoreno.roomplanner.adapter.RoomAdapter
import com.adrianmoreno.roomplanner.controller.RoomController
import com.adrianmoreno.roomplanner.models.Room
import com.google.android.material.chip.Chip
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

        //  Crear nueva instancia de RoomsFragment con argumentos.
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
        // Recuperar argumentos obligatorios
        hotelId = arguments?.getString(ARG_HOTEL_ID)
            ?: throw IllegalArgumentException("RoomsFragment requires a hotelId")
        role    = arguments?.getString(ARG_USER_ROLE)!!
        val hotelName = arguments?.getString(ARG_HOTEL_NAME) ?: ""
        // Escribir el nombre del hotel
        activity?.title = hotelName


    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflamos el layout fragment_rooms.xml
        val view = inflater.inflate(R.layout.fragment_rooms, container, false)

        // Inicializar RecyclerView en cuadrícula de 2 columnas
        val recycler = view.findViewById<RecyclerView>(R.id.roomsRecyclerView)
        val fabAdd = view.findViewById<Button>(R.id.fabAddRoom)
        /* Comprobar si el usuario es cleaner para esconder las partes relacionadas con el CRUD*/
        if (role == "CLEANER") {
            fabAdd.visibility = View.GONE
        }


        // Chip para filtrar Solo habitaciones sucias
        val chipOnlyDirty = view.findViewById<Chip>(R.id.chipOnlyDirty)
        chipOnlyDirty.setOnCheckedChangeListener { _, checked ->
            controller.rooms.value?.let { list ->
                val filtered = if (checked) list.filter { !it.isClean } else list
                // aplicar mismo orden por número:
                val sorted = filtered.sortedBy { room ->
                    room.number.filter { it.isDigit() }
                        .toIntOrNull() ?: Int.MAX_VALUE
                }
                adapter.submitList(sorted)
            }
        }


        // Configurar adapter con callbacks
        recycler.layoutManager = GridLayoutManager(context, 2)
        adapter = RoomAdapter(
            // Actualizar campo isClean en Firestore
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

        // Observar LiveData de habitaciones para actualizar UI
        controller.rooms.observe(viewLifecycleOwner) { list ->
            // Extraemos sólo los números de `room.number` ylos  convertimos a Int para
            // ordenar la lista numéricamente
            val sorted = list.sortedBy { room ->
                room.number.filter { it.isDigit() }
                    .toIntOrNull() ?: Int.MAX_VALUE
            }
            adapter.submitList(sorted)
        }


        // Carga inicial: sincronizar estados y luego obtener habitaciones
        lifecycleScope.launch {
            controller.syncRoomStatuses(hotelId)
            controller.loadRooms(hotelId)
        }

        // Abrir diálogo de nueva habitación
        fabAdd.setOnClickListener { showAddDialog() }

        return view
    }

     //Dialogo para crear una nueva habitación
    private fun showAddDialog() {
        val v = LayoutInflater.from(context)
            .inflate(R.layout.dialog_new_room, null)
        val etNumber = v.findViewById<EditText>(R.id.editRoomNumber)
        val spinnerCat  = v.findViewById<Spinner>(R.id.spinnerCategory)
        val etPrice     = v.findViewById<EditText>(R.id.editRoomPrice)

        // 1) Inicializa el Spinner con el array
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.room_categories,
            android.R.layout.simple_spinner_dropdown_item
        ).also { adapter ->
            spinnerCat.adapter = adapter
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Añadir Habitación")
            .setView(v)
            .setPositiveButton("Añadir") { _, _ ->
                val num = "Hab. " + etNumber.text.toString().trim()
                if (num.isEmpty()) {
                    Toast.makeText(context, "Número requerido", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val category = spinnerCat.selectedItem as String
                val price = etPrice.text.toString().toDoubleOrNull() ?: 0.0

                lifecycleScope.launch {
                    controller.addRoom(
                        Room(
                            id             = "",
                            hotelRef       = hotelId,
                            number         = num,
                            status         = "LIBRE",
                            isClean        = true,
                            reservedRanges = emptyList(),
                            category       = category,
                            pricePerNight  = price
                        ), hotelId
                    )
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    //Dialogo para editar
    private fun showEditDialog(room: Room) {
        val v = LayoutInflater.from(context)
            .inflate(R.layout.dialog_new_room, null)
        val etNumber = v.findViewById<EditText>(R.id.editRoomNumber)
        etNumber.setText(room.number)
        val spinnerCat = v.findViewById<Spinner>(R.id.spinnerCategory)
        val etPrice    = v.findViewById<EditText>(R.id.editRoomPrice)
        etPrice.setText(room.pricePerNight.toString())

        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.room_categories,
            android.R.layout.simple_spinner_dropdown_item
        ).also { adapter ->
            spinnerCat.adapter = adapter
            val idx = adapter.getPosition(room.category)
            if (idx >= 0) spinnerCat.setSelection(idx)
        }


        AlertDialog.Builder(requireContext())
            .setTitle("Editar Habitación")
            .setView(v)
            .setPositiveButton("Guardar") { _, _ ->
                val num      = etNumber.text.toString().trim()
                val category = spinnerCat.selectedItem as String
                val price    = etPrice.text.toString().toDoubleOrNull() ?: room.pricePerNight

                val updated = room.copy(
                    number        = num,
                    category      = category,
                    pricePerNight = price
                )
                lifecycleScope.launch {
                    controller.updateRoom(updated, hotelId)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
