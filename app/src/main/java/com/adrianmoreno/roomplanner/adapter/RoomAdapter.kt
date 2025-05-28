/**
 * RoomAdapter
 * ===========
 *
 * Un adapter para las habitaciones que muestra las habitaciones en cuadrícula.
 * Gestiona la visualización de cada `Room` y ofrece acciones de marcar limpieza,
 * editar o eliminar según los permisos (`canManage`).
 *
 * Parámetros:
 * - onToggleClean: callback al pulsar el botón de alternar limpieza (roomId, nuevo estado).
 * - onEdit: callback al pulsar "Editar" en el menú contextual, recibe el objeto Room.
 * - canManage: indica si se muestran las opciones de menú para editar/eliminar.
 * - onDelete: callback al pulsar "Eliminar", recibe el ID de la habitación.
 */

package com.adrianmoreno.roomplanner.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.adrianmoreno.roomplanner.HotelsActivity
import com.adrianmoreno.roomplanner.R
import com.adrianmoreno.roomplanner.models.Room

class RoomAdapter(
    private val onToggleClean: (roomId: String, newClean: Boolean) -> Unit,
    private val onEdit:        (Room) -> Unit,
    private val canManage:     Boolean,
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
        private val categoryTv    = view.findViewById<TextView>(R.id.tvCategory)
        private val priceTv       = view.findViewById<TextView>(R.id.tvPrice)

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
            categoryTv.text    = "Categoría: ${r.category}"
            priceTv.text       = "€ %.2f/noche".format(r.pricePerNight)
            numberTv.text      = r.number
            statusTv.text      = r.status
            cleanStatusTv.text = if (r.isClean) "Limpia" else "Sucia"
            btnToggleClean.text = if (r.isClean) "Marcar sucia" else "Marcar limpia"
            // Saquemos el rol de la Activity (DashboardActivity tiene un campo public 'role')
            val role = (itemView.context as? HotelsActivity)?.role ?: "CLEANER"
            //  Mostramos/ocultamos el menú según canManage
            btnMenu.visibility = if (canManage) View.VISIBLE else View.GONE

            //Listener del toggle
            btnToggleClean.setOnClickListener {
                onToggleClean(r.id, !r.isClean)
            }

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
