package com.example.android_front.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.android_front.R
import com.example.android_front.model.NotificationResponse

class NotificationAdapter(
    private val items: List<NotificationResponse>
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDispatchDate: TextView = itemView.findViewById(R.id.tvDispatchDate)
        val tvIsRead: TextView = itemView.findViewById(R.id.tvNotificationIsRead)
        val tvCreatedAt: TextView = itemView.findViewById(R.id.tvNotificationCreatedAt)


    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm_card, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.tvDispatchDate.text = item.payload?.let {
            it.scheduledDepartureTime?.substring(0,16)?.replace("T"," ")
        } ?: ""

        holder.tvIsRead.text = if (item.isRead) "읽음" else "미읽음"

        holder.tvCreatedAt.text = item.createdAt?.substring(0,16)?.replace("T"," ") ?: ""
    }
}

