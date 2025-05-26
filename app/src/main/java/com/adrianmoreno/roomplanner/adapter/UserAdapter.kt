// src/main/java/com/adrianmoreno/roomplanner/adapter/UserAdapter.kt
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

    private val items = mutableListOf<User>()

    /** Actualiza la lista completa */
    fun submitList(list: List<User>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val nameTv   = view.findViewById<TextView>(R.id.tvUserName)
        private val emailTv  = view.findViewById<TextView>(R.id.tvUserEmail)
        private val roleTv   = view.findViewById<TextView>(R.id.tvUserRole)
        private val btnMenu = view.findViewById<ImageView>(R.id.btnUserMenu)

        private lateinit var current: User

        init {
            btnMenu.setOnClickListener { showMenu() }
        }

        fun bind(u: User) {
            current = u
            nameTv.text  = u.name
            emailTv.text = u.email
            roleTv.text  = u.role
            // 2) Mostramos/ocultamos el menú según canManage
            btnMenu.visibility = if (canManage) View.VISIBLE else View.GONE
        }

        private fun showMenu() {
            PopupMenu(itemView.context, btnMenu).apply {
                menuInflater.inflate(R.menu.menu_item_user, menu)
                setOnMenuItemClickListener { mi ->
                    when (mi.itemId) {
                        R.id.action_edit   -> { onEdit(current); true }
                        R.id.action_delete -> { onDelete(current.uid); true }
                        else                    -> false
                    }
                }
            }.show()
        }
    }
}
