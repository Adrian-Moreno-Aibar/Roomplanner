/**
 * HotelAdapter
 * ============
 *
 * Adapter para RecyclerView que muestra la lista de hoteles.
 * Gestiona la visualización de cada `Hotel` y ofrece acciones de click,
 * editar y eliminar según los permisos (`canManage`).
 *
 * Parámetros:
 * - onClick: callback al pulsar el item (abre detalle de hotel).
 * - onEdit: callback al pulsar "Editar" en el menú contextual.
 * - onDelete: callback al pulsar "Eliminar", recibe el ID del hotel.
 * - canManage: indica si se muestran las opciones de menú para editar/eliminar.
 */
package com.adrianmoreno.roomplanner.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.adrianmoreno.roomplanner.R
import com.adrianmoreno.roomplanner.models.Hotel
import com.bumptech.glide.Glide

class HotelAdapter(
    private val onClick: (Hotel) -> Unit,
    private val onEdit:  (Hotel) -> Unit,
    private val onDelete: (String) -> Unit,
    private val canManage: Boolean
) : RecyclerView.Adapter<HotelAdapter.VH>() {

    private val items = mutableListOf<Hotel>()

    fun submitList(list: List<Hotel>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hotel, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView    = view.findViewById<ImageView>(R.id.hotelImageView)
        private val nameTv       = view.findViewById<TextView>(R.id.hotelNameTextView)
        private val addressTv    = view.findViewById<TextView>(R.id.hotelAddressTextView)
        private val btnMenu     = view.findViewById<ImageView>(R.id.btnHotelMenu)
        private var currentHotel: Hotel? = null

        init {
            itemView.setOnClickListener {
                currentHotel?.let(onClick)
            }
            btnMenu.setOnClickListener {
                showPopupMenu()
            }
        }

        fun bind(hotel: Hotel) {
            currentHotel = hotel
            nameTv.text = hotel.name
            addressTv.text = hotel.address
            btnMenu.visibility = if (canManage) View.VISIBLE else View.GONE

            if (hotel.photoUrl.isNotBlank()) {
                Glide.with(imageView.context)
                    .load(hotel.photoUrl)
                    .placeholder(R.drawable.hotel_placeholder)
                    .error(R.drawable.hotel_placeholder)
                    .centerCrop()
                    .into(imageView)
            } else {
                imageView.setImageResource(R.drawable.hotel_placeholder)
            }
        }

        private fun showPopupMenu() {
            currentHotel?.let { hotel ->
                PopupMenu(itemView.context, btnMenu).apply {
                    menuInflater.inflate(R.menu.menu_item_hotel, menu)
                    setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.action_edit   -> { onEdit(hotel); true }
                            R.id.action_delete -> { onDelete(hotel.id); true }
                            else               -> false
                        }
                    }
                }.show()
            }
        }
    }
}
