// src/main/java/com/adrianmoreno/roomplanner/EmployeesFragment.kt
package com.adrianmoreno.roomplanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adrianmoreno.roomplanner.adapter.UserAdapter
import com.adrianmoreno.roomplanner.controller.UserController

class EmployeesFragment : Fragment() {

    private lateinit var userCtrl: UserController
    private lateinit var adapter: UserAdapter
    private lateinit var hotelId: String

    companion object {
        private const val ARG_HOTEL_ID   = "HOTEL_ID"
        private const val ARG_HOTEL_NAME = "HOTEL_NAME"

        fun newInstance(hotelId: String, hotelName: String) = EmployeesFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_HOTEL_ID,   hotelId)
                putString(ARG_HOTEL_NAME, hotelName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inicializamos el ViewModel
        userCtrl = ViewModelProvider(this).get(UserController::class.java)

        hotelId = requireArguments().getString(ARG_HOTEL_ID)
            ?: throw IllegalArgumentException("EmployeesFragment necesita HOTEL_ID")
        val hotelName = requireArguments().getString(ARG_HOTEL_NAME) ?: ""
        activity?.title = "Empleados • $hotelName"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_employees, container, false)

        val rv  = view.findViewById<RecyclerView>(R.id.rvEmployees)
        val fab = view.findViewById<Button>(R.id.fabAddEmployee)

        // Configurar RecyclerView
        rv.layoutManager = LinearLayoutManager(context)
        adapter = UserAdapter(
            onEdit   = { /* TODO: implementar edición si hace falta aunque en principio no me interesa.*/ },
            onDelete = { uid ->
                // En lugar de borrar el usuario, eliminamos solo el hotel de su array
                userCtrl.removeCleanerFromHotel(uid, hotelId)
            }
        )
        rv.adapter = adapter

        // Carga inicial de cleaners para este hotel
        userCtrl.loadCleanersForHotel(hotelId)

        // Observamos los cambios y actualizamos la lista
        userCtrl.cleaners.observe(viewLifecycleOwner, Observer { cleanersList ->
            adapter.submitList(cleanersList)
        })

        // Abrir diálogo para invitar nuevo cleaner
        fab.setOnClickListener {
            InviteCleanerDialogFragment(hotelId)
                .show(childFragmentManager, "InviteCleaner")
        }

        return view
    }
}
