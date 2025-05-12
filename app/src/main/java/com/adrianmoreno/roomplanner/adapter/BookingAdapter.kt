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
        private val guestTv = view.findViewById<TextView>(R.id.tvGuestName)
        private val dateTv  = view.findViewById<TextView>(R.id.tvDates)
        private val menuIv  = view.findViewById<ImageView>(R.id.btnBookingMenu)
        private var current: Booking? = null

        init {
            menuIv.setOnClickListener { showPopupMenu() }
        }

        /** Vuelca los datos en los views */
        fun bind(b: Booking) {
            current = b
            guestTv.text = b.guestName
            dateTv.text  = "${dateFmt.format(b.checkInDate.toDate())}  –  ${dateFmt.format(b.checkOutDate.toDate())}"
        }

        /** Muestra el menú con acciones */
        private fun showPopupMenu() {
            current?.let { booking ->
                PopupMenu(itemView.context, menuIv).apply {
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
