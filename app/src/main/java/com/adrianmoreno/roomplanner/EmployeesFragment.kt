// src/main/java/com/adrianmoreno/roomplanner/EmployeesFragment.kt
package com.adrianmoreno.roomplanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
        // Inicializamos el ViewModel aquí
        userCtrl = ViewModelProvider(this).get(UserController::class.java)

        hotelId = requireArguments().getString(ARG_HOTEL_ID)
            ?: throw IllegalArgumentException("EmployeesFragment needs HOTEL_ID")
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
            onEdit   = { /* TODO: implementar edición */ },
            onDelete = { uid -> userCtrl.deleteUser(uid,hotelId) }
        )
        rv.adapter = adapter

        // Iniciar la carga de cleaners para este hotel
        userCtrl.loadCleanersForHotel(hotelId)

        // Observar únicamente la lista de cleaners filtrados por el repositorio
        userCtrl.cleaners.observe(viewLifecycleOwner, Observer { cleanersList ->
            adapter.submitList(cleanersList)
        })

        // Abrir diálogo para crear nuevo cleaner
        fab.setOnClickListener {
            InviteCleanerDialogFragment(hotelId)
                .show(childFragmentManager, "InviteCleaner")
        }

        return view
    }
}
