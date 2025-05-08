package com.adrianmoreno.roomplanner

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adrianmoreno.roomplanner.adapter.HotelAdapter
import com.adrianmoreno.roomplanner.controller.HotelController
import com.adrianmoreno.roomplanner.models.Hotel
import com.adrianmoreno.roomplanner.models.User
import com.adrianmoreno.roomplanner.RoomsActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    // ViewModel que maneja la lógica de negocio de hoteles
    private val controller: HotelController by viewModels()

    // Adapter para el RecyclerView
    private lateinit var adapter: HotelAdapter

    // Rol del usuario actual ("SUPERADMIN", "ADMIN" o "CLEANER")
    private lateinit var role: String

    // Lista de IDs de hoteles que el usuario administra o limpia
    private lateinit var hotelIds: MutableList<String>

    // Modelo de usuario local, usado para pasar hotelRefs al controlador
    private lateinit var currentUser: User

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // 1) Recuperar rol y lista de hoteles del Intent
        role     = intent.getStringExtra("USER_ROLE") ?: ""
        hotelIds = intent.getStringArrayListExtra("USER_HOTELS")
            ?.toMutableList() ?: mutableListOf()
        currentUser = User(
            uid       = FirebaseAuth.getInstance().currentUser!!.uid,
            email     = "",
            role      = role,
            hotelRefs = hotelIds
        )

        // 2) Obtener referencias a vistas del layout
        val titleTv  = findViewById<TextView>(R.id.titleTextView)
        val recycler = findViewById<RecyclerView>(R.id.hotelsRecyclerView)
        val fabAdd   = findViewById<ImageView>(R.id.fabAddHotel)

        // 3) Ajustar título según rol
        titleTv.text = when (role) {
            "SUPERADMIN" -> "Todos los hoteles"
            "ADMIN"      -> "Tus hoteles"
            "CLEANER"    -> "Hoteles asignados"
            else         -> "Hoteles"
        }

        // 4) Configurar RecyclerView con un LinearLayoutManager
        recycler.layoutManager = LinearLayoutManager(this)

        // 5) Crear el adapter pasando los tres callbacks:
        //    - onClick: abre RoomsActivity
        //    - onEdit: abre diálogo de edición
        //    - onDelete: confirma y borra
        adapter = HotelAdapter(
            onClick  = { hotel ->
                // Al tocar un hotel, abrimos la pantalla de habitaciones
                startActivity(Intent(this, RoomsActivity::class.java).apply {
                    putExtra("HOTEL_ID", hotel.id)
                    putExtra("HOTEL_NAME", hotel.name)
                })
            },
            onEdit   = { hotel -> showEditHotelDialog(hotel) },
            onDelete = { hotelId -> confirmDeleteHotel(hotelId) }
        )
        recycler.adapter = adapter

        // 6) Observar la lista de hoteles del ViewModel
        controller.hotels.observe(this) { list ->
            adapter.submitList(list)
        }

        // 7) Carga inicial de la lista
        refreshList()

        // 8) Configuración del botón de añadir (fabAdd)
        fabAdd.setOnClickListener {
            // Muestra diálogo para pedir nombre, dirección y foto
            showAddHotelDialog { name, address, photoUrl ->
                lifecycleScope.launch {
                    // Creamos directamente usando el repositorio para obtener el nuevo ID
                    val newId = controller.repo.createHotel(
                        Hotel(id = "", name = name, address = address,
                            createdBy = currentUser.uid, photoUrl = photoUrl)
                    )
                    if (newId != null) {
                        // Si no es SUPERADMIN, añadimos el ID a la lista local
                        if (role != "SUPERADMIN") {
                            hotelIds.add(newId)
                            currentUser = currentUser.copy(hotelRefs = hotelIds)
                        }
                        // Recargamos con la lista actualizada
                        refreshList()
                        runOnUiThread {
                            Toast.makeText(this@HomeActivity,
                                "Hotel creado", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@HomeActivity,
                                "Error creando hotel", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    /** Refresca la lista de hoteles según el rol y datos de usuario */
    private fun refreshList() {
        if (role == "SUPERADMIN") {
            // Superadmin ve todos los hoteles
            controller.loadAllHotels()
        } else {
            // Admin / Cleaner ve solo sus hoteles actuales
            controller.loadHotelsForUser(currentUser)
        }
    }

    /**
     * Muestra un diálogo para añadir un hotel,
     * llama onAdd cuando el usuario confirma.
     */
    private fun showAddHotelDialog(onAdd: (String, String, String) -> Unit) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialogo_crear_hotel, null)
        val etName  = dialogView.findViewById<EditText>(R.id.editHotelName)
        val etAddr  = dialogView.findViewById<EditText>(R.id.editHotelAddress)
        val etPhoto = dialogView.findViewById<EditText>(R.id.editHotelPhoto)

        AlertDialog.Builder(this)
            .setTitle("Añadir Nuevo Hotel")
            .setView(dialogView)
            .setPositiveButton("Añadir") { _, _ ->
                val name     = etName.text.toString().trim()
                val address  = etAddr.text.toString().trim()
                val photoUrl = etPhoto.text.toString().trim()
                if (name.isNotEmpty() && address.isNotEmpty()) {
                    onAdd(name, address, photoUrl)
                } else {
                    Toast.makeText(this,
                        "Nombre y dirección requeridos",
                        Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Muestra un diálogo para editar un hotel ya existente.
     * Al guardar, lanza update y refresca la lista.
     */
    private fun showEditHotelDialog(hotel: Hotel) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialogo_crear_hotel, null)
        val etName  = dialogView.findViewById<EditText>(R.id.editHotelName)
        val etAddr  = dialogView.findViewById<EditText>(R.id.editHotelAddress)
        val etPhoto = dialogView.findViewById<EditText>(R.id.editHotelPhoto)

        // Pre-llenamos con los datos actuales
        etName.setText(hotel.name)
        etAddr.setText(hotel.address)
        etPhoto.setText(hotel.photoUrl)

        AlertDialog.Builder(this)
            .setTitle("Editar Hotel")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val updated = hotel.copy(
                    name     = etName.text.toString().trim(),
                    address  = etAddr.text.toString().trim(),
                    photoUrl = etPhoto.text.toString().trim()
                )
                lifecycleScope.launch {
                    controller.repo.updateHotel(updated)
                    // Después de editar, recargamos
                    refreshList()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Muestra confirmación antes de borrar un hotel y
     * sus habitaciones (borrado en cascada).
     */
    private fun confirmDeleteHotel(hotelId: String) {
        AlertDialog.Builder(this)
            .setTitle("Borrar Hotel")
            .setMessage("¿Seguro que deseas borrar este hotel y todas sus habitaciones?")
            .setPositiveButton("Borrar") { _, _ ->
                lifecycleScope.launch {
                    // Borrado en cascada desde el repositorio
                    val ok = controller.repo.deleteHotelCascade(hotelId)
                    if (ok) {
                        // Si no es superadmin, quitamos de la lista local
                        if (role != "SUPERADMIN") {
                            hotelIds.remove(hotelId)
                            currentUser = currentUser.copy(hotelRefs = hotelIds)
                        }
                        // Recargamos tras borrar
                        refreshList()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
