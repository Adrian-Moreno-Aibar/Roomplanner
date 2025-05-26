package com.adrianmoreno.roomplanner

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adrianmoreno.roomplanner.adapter.BookingAdapter
import com.adrianmoreno.roomplanner.controller.HotelController
import com.adrianmoreno.roomplanner.models.Booking
import com.adrianmoreno.roomplanner.models.Room
import com.adrianmoreno.roomplanner.models.User
import com.adrianmoreno.roomplanner.repositories.BookingRepository
import com.google.android.material.navigation.NavigationView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DashboardActivity : AppCompatActivity() {

    companion object {
        const val REQ_JOIN = 1001
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var hotelController: HotelController
    private val repo = BookingRepository()

    private val db = FirebaseFirestore.getInstance()

    private lateinit var currentUser: User
    private lateinit var role: String
    private lateinit var hotelIds: List<String>
    private lateinit var tvWelcome: TextView

    private lateinit var hotelMap: Map<String, String>
    private lateinit var roomMap: Map<String, String>
    private lateinit var reservationAdapter: BookingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        tvWelcome = findViewById(R.id.tvWelcome)

        // 0) Init ViewModel
        hotelController = ViewModelProvider(this).get(HotelController::class.java)

        // 0.5) Cargamos el perfil completo del usuario para leer su nombre
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { snap ->
                    val user = snap.toObject(User::class.java)
                    user?.let {
                        tvWelcome.text = "¡Hola, ${it.name}!!"
                    }
                }
                .addOnFailureListener {
                    tvWelcome.text = "Bienvenid@!!"
                }
        }


        // 1) Recuperar rol y hoteles del Intent
        role     = intent.getStringExtra("USER_ROLE") ?: ""
        hotelIds = intent.getStringArrayListExtra("USER_HOTELS")?.toList() ?: emptyList()
        currentUser = User(
            uid       = FirebaseAuth.getInstance().currentUser!!.uid,
            email     = "",
            role      = role,
            hotelRefs = hotelIds
        )

        // 2) Carga de mapas antes de métricas y lista
        loadHotelMap()

        // 3) Drawer / NavigationView
        drawerLayout   = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)

        // Header
        val headerView     = navigationView.getHeaderView(0)
        val headerEmail    = headerView.findViewById<TextView>(R.id.header_email)
        val headerUsername = headerView.findViewById<TextView>(R.id.header_username)
        FirebaseAuth.getInstance().currentUser?.let { user ->
            headerEmail.text      = user.email
            user.displayName?.let { headerUsername.text = it }
        }

        navigationView.setNavigationItemSelectedListener { handleMenuSelection(it) }
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout,
            R.string.open_drawer, R.string.close_drawer
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadHotelMap() {
        if (hotelIds.isEmpty()) {
            hotelMap = emptyMap()
            loadRoomMap()
            return
        }
        db.collection("hotels")
            .whereIn(FieldPath.documentId(), hotelIds)
            .get()
            .addOnSuccessListener { snaps ->
                hotelMap = snaps.associate { it.id to (it.getString("name") ?: "") }
                loadRoomMap()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this,
                    "Error cargando hoteles: ${e.localizedMessage}",
                    Toast.LENGTH_LONG).show()
                hotelMap = emptyMap()
                loadRoomMap()
            }
    }

    private fun loadRoomMap() {
        if (hotelIds.isEmpty()) {
            roomMap = emptyMap()
            initMetricsAndList()
            return
        }
        db.collection("rooms")
            .whereIn("hotelRef", hotelIds)
            .get()
            .addOnSuccessListener { snaps ->
                val rooms = snaps.mapNotNull { it.toObject(Room::class.java) }
                roomMap = rooms.associate { it.id to it.number }
                initMetricsAndList()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this,
                    "Error cargando habitaciones: ${e.localizedMessage}",
                    Toast.LENGTH_LONG).show()
                roomMap = emptyMap()
                initMetricsAndList()
            }
    }

    private fun initMetricsAndList() {
        // Métrica Hoteles
        findViewById<TextView>(R.id.tvTotalHotels).text =
            "${hotelMap.size}\nHoteles"

        // Métrica Habitaciones
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
                    val rooms = snaps.mapNotNull { it.toObject(Room::class.java) }
                    tvRooms.text = "${rooms.size}\nHabitaciones"
                    tvFree.text  = "${rooms.count { it.status == "LIBRE" }}\nLibres"
                }
                .addOnFailureListener {
                    tvRooms.text = "0\nHabitaciones"
                    tvFree.text  = "0\nLibres"
                }
        }

        // Lista de reservas
        setupReservationsList()

        // Nuevo botón reserva
        findViewById<Button>(R.id.fabNewReservation)
            .setOnClickListener {
                if (hotelIds.isEmpty()) {
                    Toast.makeText(this,
                        "No tienes hoteles asignados",
                        Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(this, BookingFormActivity::class.java).apply {
                        putExtra("USER_HOTELS", ArrayList(hotelIds))
                        putExtra("USER_ROLE", role)
                    })
                }
            }

        // Botón "Unirse a hotel"
        findViewById<Button>(R.id.buttonJoinHotel)
            .setOnClickListener {
                // Abrimos el diálogo que pide el código
                JoinHotelDialogFragment().show(supportFragmentManager, "JoinHotel")
            }
    }

    private fun setupReservationsList() {
        val rv = findViewById<RecyclerView>(R.id.rvReservations)
        rv.layoutManager = LinearLayoutManager(this)

        reservationAdapter = BookingAdapter(
            hotelMap, roomMap,
            onEdit = { b ->
                startActivity(Intent(this, BookingFormActivity::class.java).apply {
                    putExtra("BOOKING", b)
                    putExtra("USER_HOTELS", ArrayList(hotelIds))
                    putExtra("USER_ROLE", role)
                })
            },
            onDelete = { id ->
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
        if (!::reservationAdapter.isInitialized) return

        val today = Timestamp.now()
        val checkInDate = Timestamp(today.seconds - 7 * 24 * 3600, 0)
        val nextMonth   = Timestamp(today.seconds + 30 * 24 * 3600, 0)

        if (hotelIds.isEmpty()) {
            reservationAdapter.submitList(emptyList())
            return
        }

        db.collection("bookings")
            .whereIn("hotelRef", hotelIds)
            .whereGreaterThanOrEqualTo("checkInDate", checkInDate)
            .whereLessThanOrEqualTo("checkInDate", nextMonth)
            .orderBy("checkInDate", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snaps ->
                val bookings = snaps.mapNotNull { it.toObject(Booking::class.java) }
                reservationAdapter.submitList(bookings)
            }
            .addOnFailureListener {
                Toast.makeText(this,
                    "Error cargando reservas: ${it.localizedMessage}",
                    Toast.LENGTH_SHORT).show()
            }
    }

    override fun onResume() {
        super.onResume()
        // Barrer check-outs pendientes
        lifecycleScope.launch {
            repo.sweepCheckout()
            loadUpcomingReservations()
        }
    }

    // Recibe el resultado de AcceptInvitationActivity
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_JOIN && resultCode == RESULT_OK) {
            // El user se ha unido a un nuevo hotel: recargamos todo
            // Primero, re-leemos su lista de hotelIds desde Firestore
            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { snap ->
                        val updated = snap.toObject(User::class.java)
                        if (updated != null) {
                            hotelIds = updated.hotelRefs
                            // recarga mapas y métricas
                            loadHotelMap()
                        }
                    }
            }
        }
    }

    /**
     * Lanza la pantalla de aceptar invitación como startActivityForResult.
     */
    fun launchAcceptInvitation(token: String) {
        val intent = Intent(this, AcceptInvitationActivity::class.java)
            .putExtra("INV_TOKEN", token)
        startActivityForResult(intent, REQ_JOIN)
    }

    // Menú lateral
    private fun handleMenuSelection(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dash -> {
                // Dejar vacío, porque ya estas aquí
            }
            R.id.nav_hotel -> {
                startActivity(Intent(this, HotelsActivity::class.java).apply {
                    putExtra("USER_ROLE", role)
                    putStringArrayListExtra("USER_HOTELS", ArrayList(hotelIds))
                })
            }
            R.id.nav_logout -> {
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            drawerLayout.openDrawer(GravityCompat.START)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }

    fun reloadHotels() {
        // 1) Volver a obtener la lista de hoteles del usuario (viene por Intent o desde FirebaseAuth)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Si quisieras leerlos de Firestore:
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { snap ->
                val updatedRefs = snap.get("hotelRefs") as? List<String> ?: emptyList()
                // Actualiza tu estado interno
                hotelIds = updatedRefs
                // Y vuelve a disparar la carga de mapas y métricas
                loadHotelMap()
            }
            .addOnFailureListener {
                Toast.makeText(this,
                    "No se pudieron recargar hoteles: ${it.localizedMessage}",
                    Toast.LENGTH_LONG).show()
            }
    }
/*
funcion movida a BookingRepository
    fun sweepCheckout() {
        val now = Timestamp.now()
        db.collection("bookings")
            .whereLessThanOrEqualTo("checkOutDate", now)
            .get()
            .addOnSuccessListener { snaps ->
                snaps.forEach { doc ->
                    val b = doc.toObject(Booking::class.java)
                    // elimina la reserva (o márcala usada) y ensucia la habitación
                    lifecycleScope.launch {
                        repo.markRoomFreeAndDirty(b.roomRef)
                        doc.reference.delete().await()
                    }
                }
            }
    }

 */
}
