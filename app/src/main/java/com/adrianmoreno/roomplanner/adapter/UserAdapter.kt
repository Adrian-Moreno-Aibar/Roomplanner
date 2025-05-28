/**
 * UserAdapter
 * ===========
 *
 * Un adapter para RecyclerView que muestra una lista de usuarios (cleaners/admins).
 * Gestiona la visualización de cada elemento y ofrece acciones de editar o eliminar
 * según los permisos (`canManage`).
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
import com.adrianmoreno.roomplanner.models.User

class UserAdapter(
    private val canManage: Boolean,
    private val onEdit:   (User)   -> Unit,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<UserAdapter.VH>() {

    // Lista interna de elementos a mostrar
    private val items = mutableListOf<User>()


    //Sustituye la lista mostrada por `list` y notifica al RecyclerView.

    fun submitList(list: List<User>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        // Infla el layout de cada fila de usuario
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        // Vincula el dato de `items[position]` con la ViewHolder
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size


     //ViewHolder que contiene las vistas de un único elemento usuario

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val nameTv   = view.findViewById<TextView>(R.id.tvUserName)
        private val emailTv  = view.findViewById<TextView>(R.id.tvUserEmail)
        private val roleTv   = view.findViewById<TextView>(R.id.tvUserRole)
        private val btnMenu  = view.findViewById<ImageView>(R.id.btnUserMenu)

        private lateinit var current: User

        init {
            // Al pulsar el icono, muestra el menú contextual
            btnMenu.setOnClickListener { showMenu() }
        }


         //Asocia los datos del usuario a las vistas y gestiona visibilidad de menú.

        fun bind(user: User) {
            current = user
            nameTv.text  = user.name
            emailTv.text = user.email
            roleTv.text  = user.role
            // Mostrar o no el botón de opciones según permiso
            btnMenu.visibility = if (canManage) View.VISIBLE else View.GONE
        }

        /**
         * Muestra un PopupMenu con acciones "Editar" y "Eliminar".
         */
        private fun showMenu() {
            PopupMenu(itemView.context, btnMenu).apply {
                menuInflater.inflate(R.menu.menu_item_user, menu)
                setOnMenuItemClickListener { mi ->
                    when (mi.itemId) {
                        R.id.action_edit   -> { onEdit(current); true }
                        R.id.action_delete -> { onDelete(current.uid); true }
                        else               -> false
                    }
                }
            }.show()
        }
    }
}
