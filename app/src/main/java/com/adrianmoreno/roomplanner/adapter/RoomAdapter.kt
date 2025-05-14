package com.adrianmoreno.roomplanner.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.adrianmoreno.roomplanner.R
import com.adrianmoreno.roomplanner.models.Room

class RoomAdapter(
    private val onToggleClean: (roomId: String, newClean: Boolean) -> Unit,
    private val onEdit:        (Room) -> Unit,
    private val onDelete:      (String) -> Unit
) : RecyclerView.Adapter<RoomAdapter.VH>() {

    private val items = mutableListOf<Room>()

    fun submitList(list: List<Room>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val numberTv       = view.findViewById<TextView>(R.id.roomNumber)
        private val statusTv       = view.findViewById<TextView>(R.id.roomStatus)
        private val cleanStatusTv  = view.findViewById<TextView>(R.id.roomCleanStatus)
        private val btnToggleClean = view.findViewById<Button>(R.id.btnToggleClean)
        private val btnMenu        = view.findViewById<ImageView>(R.id.btnRoomMenu)
        private var current: Room? = null

        init {
            btnToggleClean.setOnClickListener {
                current?.let {
                    val toggled = !it.isClean
                    onToggleClean(it.id, toggled)
                }
            }
            btnMenu.setOnClickListener { showPopupMenu() }
        }

        fun bind(r: Room) {
            current = r
            numberTv.text      = r.number
            statusTv.text      = r.status
            cleanStatusTv.text = if (r.isClean) "Limpia" else "Sucia"
            btnToggleClean.text = if (r.isClean) "Marcar sucia" else "Marcar limpia"
        }

        private fun showPopupMenu() {
            current?.let { room ->
                PopupMenu(itemView.context, btnMenu).apply {
                    menuInflater.inflate(R.menu.menu_item_room, menu)
                    setOnMenuItemClickListener { mi ->
                        when (mi.itemId) {
                            R.id.action_edit   -> { onEdit(room); true }
                            R.id.action_delete -> { onDelete(room.id); true }
                            else               -> false
                        }
                    }
                }.show()
            }
        }
    }
}
