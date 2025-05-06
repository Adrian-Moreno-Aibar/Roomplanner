package com.adrianmoreno.roomplanner

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adrianmoreno.roomplanner.R
import com.adrianmoreno.roomplanner.adapter.HotelAdapter
import com.adrianmoreno.roomplanner.models.Hotel
import com.adrianmoreno.roomplanner.repositories.HotelRepository
import com.adrianmoreno.roomplanner.RoomsActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private val repo = HotelRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val role     = intent.getStringExtra("USER_ROLE") ?: ""
        val hotelIds = intent.getStringArrayListExtra("USER_HOTELS")?.toMutableList() ?: mutableListOf()

        val titleTv  = findViewById<TextView>(R.id.titleTextView)
        val recycler = findViewById<RecyclerView>(R.id.hotelsRecyclerView)
        val fabAdd   = findViewById<ImageView>(R.id.fabAddHotel)

        recycler.layoutManager = LinearLayoutManager(this)
        val adapter = HotelAdapter { hotel ->
            startActivity(Intent(this, RoomsActivity::class.java).apply {
                putExtra("HOTEL_ID", hotel.id)
                putExtra("HOTEL_NAME", hotel.name)
            })
        }
        recycler.adapter = adapter

        titleTv.text = when (role) {
            "SUPERADMIN" -> "Todos los hoteles"
            "ADMIN"      -> "Tus hoteles"
            "CLEANER"    -> "Hoteles asignados"
            else         -> "Hoteles"
        }

        fun refreshList() {
            if (role == "SUPERADMIN") {
                repo.getAllHotels { adapter.submitList(it) }
            } else {
                repo.getHotelsForUser(hotelIds) { adapter.submitList(it) }
            }
        }
        refreshList()

        fabAdd.setOnClickListener {
            showAddHotelDialog { name, address, photoUrl ->
                val uid = FirebaseAuth.getInstance().currentUser!!.uid
                val h = Hotel(id = "", name = name, address = address, photoUrl = photoUrl, createdBy = uid)
                lifecycleScope.launch {
                    val newId = repo.createHotel(h)
                    runOnUiThread {
                        if (newId != null) {
                            hotelIds.add(newId)
                            refreshList()
                            Toast.makeText(this@HomeActivity, "Hotel creado", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@HomeActivity, "Error creando hotel", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun showAddHotelDialog(onAdd: (String, String, String) -> Unit) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialogo_crear_hotel, null)
        val etName    = dialogView.findViewById<EditText>(R.id.editHotelName)
        val etAddr    = dialogView.findViewById<EditText>(R.id.editHotelAddress)
        val etPhoto   = dialogView.findViewById<EditText>(R.id.editHotelPhoto)

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
                    Toast.makeText(this, "Debe rellenar nombre y dirección", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
