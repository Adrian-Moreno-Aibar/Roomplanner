package com.adrianmoreno.roomplanner

import android.app.DatePickerDialog
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.adrianmoreno.roomplanner.models.Booking
import com.adrianmoreno.roomplanner.models.Hotel
import com.adrianmoreno.roomplanner.models.Room
import com.adrianmoreno.roomplanner.repositories.BookingRepository
import com.adrianmoreno.roomplanner.util.DateParser
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.*

class BookingFormActivity : AppCompatActivity() {

    private lateinit var etGuestName: TextInputEditText
    private lateinit var spinnerHotels: Spinner
    private lateinit var spinnerRooms: Spinner
    private lateinit var etCheckIn: TextInputEditText
    private lateinit var etCheckOut: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var btnDelete: Button

    private val db = FirebaseFirestore.getInstance()
    private val repo = BookingRepository()

    private var existing: Booking? = null
    private lateinit var hotelIds: List<String>
    private var hotelsList: List<Hotel> = emptyList()
    private var roomsList: List<Room> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking_form)

        // 1) findViewById
        etGuestName   = findViewById(R.id.etGuestName)
        spinnerHotels = findViewById(R.id.spinnerHotels)
        spinnerRooms  = findViewById(R.id.spinnerRooms)
        etCheckIn     = findViewById(R.id.etCheckIn)
        etCheckOut    = findViewById(R.id.etCheckOut)
        btnSave       = findViewById(R.id.btnSave)
        btnDelete     = findViewById(R.id.btnDelete)

        // 2) Leer lista de hoteles del Intent
        hotelIds = intent.getStringArrayListExtra("USER_HOTELS")?.toList() ?: emptyList()
        if (hotelIds.isEmpty()) {
            Toast.makeText(this, "No tienes hoteles asignados", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 3) Recuperar reserva existente (edición)
        @Suppress("DEPRECATION")
        existing = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("BOOKING", Booking::class.java)
        } else {
            intent.getParcelableExtra("BOOKING") as? Booking
        }
        existing?.let { fillForm(it) }

        // 4) Cargar hoteles
        db.collection("hotels")
            .whereIn(FieldPath.documentId(), hotelIds)
            .get()
            .addOnSuccessListener { snaps ->
                hotelsList = snaps.mapNotNull { it.toObject(Hotel::class.java) }
                spinnerHotels.adapter = ArrayAdapter(
                    this, android.R.layout.simple_spinner_dropdown_item,
                    hotelsList.map { it.name }
                )
                existing?.let { b ->
                    val idx = hotelsList.indexOfFirst { it.id == b.hotelRef }
                    if (idx >= 0) spinnerHotels.setSelection(idx)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error cargando hoteles: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }

        // 5) Al seleccionar un hotel, cargar sus habitaciones
        spinnerHotels.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long
            ) {
                val selectedHotel = hotelsList[pos]
                db.collection("rooms")
                    .whereEqualTo("hotelRef", selectedHotel.id)
                    .get()
                    .addOnSuccessListener { snaps ->
                        roomsList = snaps.mapNotNull { it.toObject(Room::class.java) }
                        spinnerRooms.adapter = ArrayAdapter(
                            this@BookingFormActivity,
                            android.R.layout.simple_spinner_dropdown_item,
                            roomsList.map { it.number }
                        )
                        existing?.let { b ->
                            val rIdx = roomsList.indexOfFirst { it.id == b.roomRef }
                            if (rIdx >= 0) spinnerRooms.setSelection(rIdx)
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this@BookingFormActivity,
                            "Error cargando habitaciones: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // 6) DatePickers
        etCheckIn.setOnClickListener { showDate { d -> etCheckIn.setText(d) } }
        etCheckOut.setOnClickListener { showDate { d -> etCheckOut.setText(d) } }

        // 7) Guardar o editar
        btnSave.setOnClickListener {
            val guest = etGuestName.text.toString().trim()
            val inStr = etCheckIn.text.toString().trim()
            val outStr= etCheckOut.text.toString().trim()
            if (guest.isEmpty() || inStr.isEmpty() || outStr.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val checkIn  = Timestamp(DateParser.parse(inStr))
            val checkOut = Timestamp(DateParser.parse(outStr))
            if (checkOut.seconds <= checkIn.seconds) {
                Toast.makeText(this, "La salida debe ser después del check-in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val hotel = hotelsList[spinnerHotels.selectedItemPosition]
            val room  = roomsList.getOrNull(spinnerRooms.selectedItemPosition)
            if (room == null) {
                Toast.makeText(this, "Selecciona una habitación", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val booking = (existing ?: Booking()).copy(
                guestName    = guest,
                checkInDate  = checkIn,
                checkOutDate = checkOut,
                hotelRef     = hotel.id,
                roomRef      = room.id
            )

            lifecycleScope.launch {
                if (existing == null) {
                    val newId = repo.createIfAvailable(booking)
                    if (newId != null) {
                        Toast.makeText(this@BookingFormActivity, "Reserva creada", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(
                            this@BookingFormActivity,
                            "No se pudo crear (conflicto de fechas)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    val ok = repo.updateIfAvailable(booking)
                    if (ok) {
                        Toast.makeText(this@BookingFormActivity, "Reserva actualizada", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(
                            this@BookingFormActivity,
                            "No se pudo actualizar (conflicto de fechas)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        // 8) Borrar (solo si existe reserva)
        btnDelete.isEnabled = existing != null
        btnDelete.setOnClickListener {
            existing?.let { b ->
                AlertDialog.Builder(this)
                    .setTitle("Borrar reserva")
                    .setMessage("¿Eliminar reserva de ${b.guestName}?")
                    .setPositiveButton("Borrar") { _, _ ->
                        lifecycleScope.launch {
                            if (repo.delete(b.id)) {
                                Toast.makeText(this@BookingFormActivity, "Reserva borrada", Toast.LENGTH_SHORT).show()
                                finish()
                            } else {
                                Toast.makeText(this@BookingFormActivity, "Error al borrar", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    private fun fillForm(b: Booking) {
        etGuestName.setText(b.guestName)
        etCheckIn.setText(DateParser.format(b.checkInDate.toDate()))
        etCheckOut.setText(DateParser.format(b.checkOutDate.toDate()))
    }

    private fun showDate(onDate: (String) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                onDate(String.format("%02d/%02d/%04d", d, m + 1, y))
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}
