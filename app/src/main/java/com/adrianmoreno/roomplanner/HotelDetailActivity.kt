// src/main/java/com/adrianmoreno/roomplanner/HotelDetailActivity.kt
package com.adrianmoreno.roomplanner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class HotelDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HOTEL_ID = "HOTEL_ID"
        const val EXTRA_HOTEL_NAME = "HOTEL_NAME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hotel_detail)

        // Recogemos el hotelId que viene de la lista de hoteles
        val hotelId = intent.getStringExtra(EXTRA_HOTEL_ID)
            ?: throw IllegalArgumentException("Hotel ID missing")
        val hotelName = intent.getStringExtra(EXTRA_HOTEL_NAME)
            ?: ""

        // Si tienes un Toolbar, podrías poner:
        supportActionBar?.title = hotelName

        // Cargamos por defecto el fragment de Habitaciones
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_container,
                RoomsFragment.newInstance(hotelId,hotelName)
            )
            .commit()

        // Configuramos la navegación inferior
        findViewById<BottomNavigationView>(R.id.bottom_nav)
            .setOnNavigationItemSelectedListener { item ->
                val fragment = when (item.itemId) {
                    R.id.nav_rooms ->
                        RoomsFragment.newInstance(hotelId,hotelName)
                    R.id.nav_employees ->
                        EmployeesFragment.newInstance(hotelId,hotelName)
                    else -> null
                }
                fragment?.let {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, it)
                        .commit()
                    true
                } ?: false
            }
    }
}
