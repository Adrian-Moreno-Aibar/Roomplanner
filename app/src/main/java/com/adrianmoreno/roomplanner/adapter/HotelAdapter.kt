// com/example/roomplanner/ui/HotelAdapter.kt
package com.example.roomplanner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.adrianmoreno.roomplanner.R
import com.adrianmoreno.roomplanner.models.Hotel

class HotelAdapter(
    private val onClick: (Hotel) -> Unit
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
        return VH(view, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class VH(view: View, val onClick: (Hotel) -> Unit)
        : RecyclerView.ViewHolder(view) {
        private val nameTv    = view.findViewById<TextView>(R.id.hotelNameTextView)
        private val addressTv = view.findViewById<TextView>(R.id.hotelAddressTextView)
        private var current: Hotel? = null

        init {
            view.setOnClickListener {
                current?.let(onClick)
            }
        }

        fun bind(h: Hotel) {
            current = h
            nameTv.text    = h.name
            addressTv.text = h.address
        }
    }
}
