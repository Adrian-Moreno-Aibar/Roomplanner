/**
 * BookingAdapter
 * ==============
 *
 * Adapter para RecyclerView que muestra una lista de reservas (`Booking`).
 * Gestiona:
 * - Mostrar datos de reserva (cliente, hotel, habitación, fechas y observaciones).
 * - Opciones "Editar" y "Eliminar" en un menú contextual (según `canManage`).
 *
 * Parámetros:
 * - hotelMap: mapeo hotelRef -> nombre del hotel.
 * - roomMap: mapeo roomRef -> número de habitación.
 * - canManage: indica si se muestran las opciones de menú para editar/eliminar.
 * - onEdit: callback al pulsar "Editar", recibe el objeto Booking.
 * - onDelete: callback al pulsar "Eliminar", recibe el ID de la reserva.
 */
package com.adrianmoreno.roomplanner.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.adrianmoreno.roomplanner.R
import com.adrianmoreno.roomplanner.models.Booking
import java.text.SimpleDateFormat
import java.util.*

class BookingAdapter(
    private val hotelMap: Map<String, String>,
    private val roomMap:  Map<String, String>,
    private val canManage: Boolean,
    private val onEdit:   (Booking) -> Unit,
    private val onDelete: (String)  -> Unit
) : RecyclerView.Adapter<BookingAdapter.VH>() {

    private val items = mutableListOf<Booking>()
    private val dateFmt = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

    /** Actualiza la lista completa */
    fun submitList(list: List<Booking>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booking, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val guestTv        = view.findViewById<TextView>(R.id.tvGuestName)
        private val hotelTv        = view.findViewById<TextView>(R.id.tvHotelName)
        private val roomTv         = view.findViewById<TextView>(R.id.tvRoomNumber)
        private val dateTv         = view.findViewById<TextView>(R.id.tvDates)
        private val observationsTv = view.findViewById<TextView>(R.id.tvObservations)
        private val btnMenu         = view.findViewById<ImageView>(R.id.btnBookingMenu)
        private var current: Booking? = null

        init {
            btnMenu.setOnClickListener { showPopupMenu() }
        }

        /** Vuelca los datos en los views */
        fun bind(b: Booking) {
            current = b
            guestTv.text = b.guestName
            hotelTv.text = hotelMap[b.hotelRef] ?: "–"
            roomTv.text  = roomMap[b.roomRef]   ?: "–"
            dateTv.text  = "${dateFmt.format(b.checkInDate.toDate())}  –  ${dateFmt.format(b.checkOutDate.toDate())}"

            // Mostramos/ocultamos el menú según canManage
            btnMenu.visibility = if (canManage) View.VISIBLE else View.GONE
            // Observaciones
            if (b.observations.isNullOrBlank()) {
                observationsTv.visibility = View.GONE
            } else {
                observationsTv.text = "Observaciones: ${b.observations}"
                observationsTv.visibility = View.VISIBLE
            }
        }

        /** Muestra el menú con acciones */
        private fun showPopupMenu() {
            current?.let { booking ->
                PopupMenu(itemView.context, btnMenu).apply {
                    menuInflater.inflate(R.menu.menu_item_booking, menu)
                    setOnMenuItemClickListener { mi ->
                        when (mi.itemId) {
                            R.id.action_edit_booking -> {
                                onEdit(booking)
                                true
                            }
                            R.id.action_delete_booking -> {
                                onDelete(booking.id)
                                true
                            }
                            else -> false
                        }
                    }
                }.show()
            }
        }
    }
}
