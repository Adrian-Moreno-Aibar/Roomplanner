package com.adrianmoreno.roomplanner.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.adrianmoreno.roomplanner.R

class NotificationAdapter : RecyclerView.Adapter<NotificationAdapter.VH>() {
    private val items = mutableListOf<String>()
    fun submitList(list: List<String>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(
        LayoutInflater.from(p.context)
            .inflate(android.R.layout.simple_list_item_1, p, false) as TextView
    )
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        h.tv.text = items[pos]
    }
    class VH(val tv: TextView): RecyclerView.ViewHolder(tv)
}
