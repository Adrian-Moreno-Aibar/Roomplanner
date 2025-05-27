package com.adrianmoreno.roomplanner

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
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
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch

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
    private lateinit var bookingAdapter: BookingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        tvWelcome = findViewById(R.id.tvWelcome)

        // Inicializamos el ViewModel de hoteles
        hotelController = ViewModelProvider(this).get(HotelController::class.java)

        // Cargamos el nombre real del usuario para el saludo
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { snap ->
                    snap.toObject(User::class.java)?.let { user ->
                        tvWelcome.text = "¡Hola, ${user.name}!!"
                    }
                }
                .addOnFailureListener {
                    tvWelcome.text = "Bienvenid@!!"
                }
        }

        // Recuperamos rol y lista de hoteles del Intent
        role     = intent.getStringExtra("USER_ROLE") ?: ""
        hotelIds = intent.getStringArrayListExtra("USER_HOTELS")?.toList() ?: emptyList()
        currentUser = User(
            uid       = FirebaseAuth.getInstance().currentUser!!.uid,
            email     = "",
            role      = role,
            hotelRefs = hotelIds
        )

        // Carga inicial de mapas (hoteles / habitaciones) antes de mostrar métricas
        loadHotelMap()

        // Configuramos drawer
        drawerLayout   = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener { handleMenuSelection(it) }
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout,
            R.string.open_drawer, R.string.close_drawer
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Obtenemos la vista del header
        val headerView   = navigationView.getHeaderView(0)
        val avatarIv     = headerView.findViewById<ImageView>(R.id.header_avatar)
        val nameTv       = headerView.findViewById<TextView>(R.id.header_username)
        val editIv       = headerView.findViewById<ImageView>(R.id.header_edit)

        // Cargar datos del usuario
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid)
                .get()
                .addOnSuccessListener { snap ->
                    val user = snap.toObject(User::class.java)
                    user?.let {
                        // Nombre
                        nameTv.text = it.name.ifEmpty { "Usuario" }
                        // Foto (o placeholder)
                        Glide.with(this)
                            .load(it.photoUrl.ifEmpty { R.drawable.gato })
                            .circleCrop()
                            .into(avatarIv)
                    }
                }
        }

        // Al pulsar el lápiz, abrimos diálogo para editar
        editIv.setOnClickListener {
            showEditProfileDialog(uid, nameTv, avatarIv)
        }

        val fabNewReservation = findViewById<Button>(R.id.fabNewReservation)
        // sólo Admin o Super pueden crear reservas
        if (role == "CLEANER") {
            fabNewReservation.visibility = View.GONE
        }

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
        // Métricas
        findViewById<TextView>(R.id.tvTotalHotels).text =
            "${hotelMap.size}\nHoteles"

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

        // Botón nueva reserva
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


        // Botón “Unirse a hotel”
        findViewById<Button>(R.id.buttonJoinHotel)
            .setOnClickListener {
                JoinHotelDialogFragment().show(supportFragmentManager, "JoinHotel")
            }
    }

    private fun setupReservationsList() {
        val rv = findViewById<RecyclerView>(R.id.rvReservations)
        rv.layoutManager = LinearLayoutManager(this)

        bookingAdapter = BookingAdapter(
            hotelMap, roomMap,
            onEdit = { booking ->
                startActivity(Intent(this, BookingFormActivity::class.java).apply {
                    putExtra("BOOKING", booking)
                    putExtra("USER_HOTELS", ArrayList(hotelIds))
                    putExtra("USER_ROLE", role)
                })
            },
            onDelete = { id ->
                // Cancelación “suave” via repositorio
                lifecycleScope.launch {
                    val ok = repo.cancelReservation(id)
                    if (ok) {
                        loadUpcomingReservations()
                    } else {
                        Toast.makeText(this@DashboardActivity,
                            "Error borrando reserva", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            canManage = (role != "CLEANER")
        )
        rv.adapter = bookingAdapter
        loadUpcomingReservations()
    }

    private fun loadUpcomingReservations() {
        if (!::bookingAdapter.isInitialized) return

        val today      = Timestamp.now()
        val checkInMin = Timestamp(today.seconds - 7 * 24 * 3600, 0)
        val checkOutMax = Timestamp(today.seconds + 30 * 24 * 3600, 0)

        if (hotelIds.isEmpty()) {
            bookingAdapter.submitList(emptyList())
            return
        }

        db.collection("bookings")
            .whereIn("hotelRef", hotelIds)
            .whereGreaterThanOrEqualTo("checkInDate", checkInMin)
            .whereLessThanOrEqualTo("checkInDate", checkOutMax)
            .orderBy("checkInDate", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snaps ->
                val bookings = snaps.mapNotNull { it.toObject(Booking::class.java) }
                bookingAdapter.submitList(bookings)
            }
            .addOnFailureListener {
                Toast.makeText(this,
                    "Error cargando reservas: ${it.localizedMessage}",
                    Toast.LENGTH_SHORT).show()
            }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            // Barrer check-outs automáticos al arrancar (ensucia y libera)
            repo.sweepCheckout(hotelIds)
            loadUpcomingReservations()
        }
    }

    // Manejo de resultado al unirse a hotel
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_JOIN && resultCode == RESULT_OK) {
            // Releer hotelRefs y recargar
            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { snap ->
                        snap.toObject(User::class.java)?.let { updated ->
                            hotelIds = updated.hotelRefs
                            loadHotelMap()
                        }
                    }
            }
        }
    }

    fun launchAcceptInvitation(token: String) {
        val intent = Intent(this, AcceptInvitationActivity::class.java)
            .putExtra("INV_TOKEN", token)
        startActivityForResult(intent, REQ_JOIN)
    }

    private fun handleMenuSelection(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dash -> { /* Ya estás aquí */ }
            R.id.nav_hotel -> startActivity(Intent(this, HotelsActivity::class.java).apply {
                putExtra("USER_ROLE", role)
                putStringArrayListExtra("USER_HOTELS", ArrayList(hotelIds))
            })
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

    /** Recarga hotéles tras unirse vía código */
    fun reloadHotels() {
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { snap ->
                    val refs = snap.get("hotelRefs") as? List<String> ?: emptyList()
                    hotelIds = refs
                    loadHotelMap()
                }
                .addOnFailureListener {
                    Toast.makeText(this,
                        "No se pudieron recargar hoteles: ${it.localizedMessage}",
                        Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun showEditProfileDialog(
        uid: String?,
        nameTv: TextView,
        avatarIv: ImageView
    ) {
        if (uid == null) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val etName     = dialogView.findViewById<EditText>(R.id.etName)
        val etPhoto    = dialogView.findViewById<EditText>(R.id.etPhotoUrl)

        // Prellenar con valores actuales
        etName.setText(nameTv.text)
        // Opcionalmente, almacena la URL previa en un campo
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { snap ->
                val url = snap.getString("photoUrl") ?: ""
                etPhoto.setText(url)
            }

        AlertDialog.Builder(this)
            .setTitle("Editar perfil")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = etName.text.toString().trim()
                val newUrl  = etPhoto.text.toString().trim()
                // Actualizamos en Firestore
                db.collection("users").document(uid)
                    .update(mapOf(
                        "name"     to newName,
                        "photoUrl" to newUrl
                    ))
                    .addOnSuccessListener {
                        // Refrescamos header
                        nameTv.text = newName
                        Glide.with(this)
                            .load(newUrl.ifEmpty { R.drawable.gato })
                            .circleCrop()
                            .into(avatarIv)
                        Toast.makeText(this, "Perfil actualizado", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
