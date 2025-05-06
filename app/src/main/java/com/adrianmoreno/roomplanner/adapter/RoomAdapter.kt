package com.adrianmoreno.roomplanner.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.adrianmoreno.roomplanner.R
import com.adrianmoreno.roomplanner.models.Room

class RoomAdapter(
    private val onToggle: (String, String) -> Unit // roomId, currentStatus
) : RecyclerView.Adapter<RoomAdapter.VH>() {
    private val items = mutableListOf<Room>()
    fun submitList(list: List<Room>) { items.apply { clear(); addAll(list) }; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room, parent, false), onToggle
    )
    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(items[pos])
    override fun getItemCount() = items.size

    class VH(view: View, val onToggle: (String, String) -> Unit)
        : RecyclerView.ViewHolder(view) {
        private val numTv    = view.findViewById<TextView>(R.id.roomNumber)
        private val statusTv = view.findViewById<TextView>(R.id.roomStatus)
        private val btn      = view.findViewById<Button>(R.id.toggleStatusButton)
        private var room: Room? = null

        init {
            btn.setOnClickListener {
                room?.let { onToggle(it.id, it.status) }
            }
        }
        fun bind(r: Room) {
            room = r
            numTv.text    = "Habitación ${r.number}"
            statusTv.text = r.status
        }
    }
}
