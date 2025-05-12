package com.adrianmoreno.roomplanner

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adrianmoreno.roomplanner.adapter.BookingAdapter
import com.adrianmoreno.roomplanner.models.Booking
import com.adrianmoreno.roomplanner.models.User
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var currentUser: User
    private lateinit var role: String
    private lateinit var hotelIds: List<String>

    private lateinit var reservationAdapter: BookingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // 1) Leer rol y hoteles del Intent
        role     = intent.getStringExtra("USER_ROLE") ?: ""
        hotelIds = intent.getStringArrayListExtra("USER_HOTELS")?.toList() ?: emptyList()
        currentUser = User(
            uid       = FirebaseAuth.getInstance().currentUser!!.uid,
            email     = "",
            role      = role,
            hotelRefs = hotelIds
        )

        // 2) Saludo + Fecha
        findViewById<TextView>(R.id.tvWelcome).text =
            "¡Hola, ${getUserName()}!"

        // 3) Métricas de hoteles con FieldPath.documentId()
        val tvHotels = findViewById<TextView>(R.id.tvTotalHotels)
        if (hotelIds.isEmpty()) {
            tvHotels.text = "0\nHoteles"
        } else {
            db.collection("hotels")
                .whereIn(FieldPath.documentId(), hotelIds)
                .get()
                .addOnSuccessListener { snaps ->
                    tvHotels.text = "${snaps.size()}\nHoteles"
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this,
                        "Error cargando hoteles: ${e.localizedMessage}",
                        Toast.LENGTH_LONG).show()
                }
        }

        // 4) Métricas de habitaciones
        val tvRooms = findViewById<TextView>(R.id.tvTotalRooms)
        val tvFree  = findViewById<TextView>(R.id.tvFreeRooms)
        if (hotelIds.isEmpty()) {
            tvRooms.text = "0\nHabitaciones"
            tvFree.text  = "0\nLibres"
        } else {
            db.collection("rooms")
                .whereIn("hotelRef", hotelIds)
                .get()
                .addOnSuccessListener { snaps ->
                    val rooms = snaps.mapNotNull {
                        it.toObject(com.adrianmoreno.roomplanner.models.Room::class.java)
                    }
                    tvRooms.text = "${rooms.size}\nHabitaciones"
                    tvFree.text  = "${rooms.count { it.status == "LIBRE" }}\nLibres"
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this,
                        "Error cargando habitaciones: ${e.localizedMessage}",
                        Toast.LENGTH_LONG).show()
                }
        }

        // 5) Lista de próximas reservas
        setupReservationsList()

        // 6) Botón nueva reserva
        findViewById<Button>(R.id.fabNewReservation)
            .setOnClickListener {
                if (hotelIds.isEmpty()) {
                    Toast.makeText(this,
                        "No tienes hoteles aún para reservar",
                        Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(
                        Intent(this, BookingFormActivity::class.java).apply {
                            putExtra("USER_HOTELS", ArrayList(hotelIds))
                            putExtra("USER_ROLE", role)
                        }
                    )
                }
            }
    }

    private fun setupReservationsList() {
        val rv = findViewById<RecyclerView>(R.id.rvReservations)
        rv.layoutManager = LinearLayoutManager(this)

        reservationAdapter = BookingAdapter(
            onEdit = { booking: Booking ->
                startActivity(
                    Intent(this, BookingFormActivity::class.java).apply {
                        putExtra("BOOKING", booking)
                        putExtra("USER_HOTELS", ArrayList(hotelIds))
                        putExtra("USER_ROLE", role)
                    }
                )
            },
            onDelete = { id: String ->
                if (hotelIds.isEmpty()) return@BookingAdapter
                db.collection("bookings").document(id).delete()
                    .addOnSuccessListener { loadUpcomingReservations() }
                    .addOnFailureListener {
                        Toast.makeText(this,
                            "Error borrando reserva: ${it.localizedMessage}",
                            Toast.LENGTH_SHORT).show()
                    }
            }
        )
        rv.adapter = reservationAdapter
        loadUpcomingReservations()
    }

    private fun loadUpcomingReservations() {
        val today     = Timestamp.now()
        val nextMonth = Timestamp(today.seconds + 30 * 24 * 3600, 0)

        if (hotelIds.isEmpty()) {
            reservationAdapter.submitList(emptyList())
            return
        }

        db.collection("bookings")
            .whereIn("hotelRef", hotelIds)
            .whereGreaterThanOrEqualTo("checkInDate", today)
            .whereLessThanOrEqualTo("checkInDate", nextMonth)
            .orderBy("checkInDate", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snaps ->
                val bookings = snaps.mapNotNull {
                    it.toObject(Booking::class.java)
                }
                reservationAdapter.submitList(bookings)
            }
            .addOnFailureListener {
                Toast.makeText(this,
                    "Error cargando reservas: ${it.localizedMessage}",
                    Toast.LENGTH_SHORT).show()
            }
    }

    private fun getUserName(): String {
        // TODO: reemplaza con tu lógica real
        return "Adrián"
    }

    override fun onResume() {
        super.onResume()
        loadUpcomingReservations()
    }

}
