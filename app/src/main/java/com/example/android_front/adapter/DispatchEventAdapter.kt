package com.example.android_front.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.android_front.R
import com.example.android_front.model.DispatchEventsResponse

class DispatchEventAdapter(
    private val eventList: List<DispatchEventsResponse>,
    private val onItemClick: (DispatchEventsResponse) -> Unit // 클릭 콜백
) : RecyclerView.Adapter<DispatchEventAdapter.EventViewHolder>() {

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvEventType: TextView = itemView.findViewById(R.id.tvEventType)
        val tvEventTime: TextView = itemView.findViewById(R.id.tvEventTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_warning_card, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = eventList[position]

        holder.tvEventType.text = event.eventType.warningName
        holder.tvEventTime.text = event.eventTimestamp.substringAfter("T").substring(0, 5)

        // 클릭 리스너
        holder.itemView.setOnClickListener {
            onItemClick(event)
        }
    }

    override fun getItemCount(): Int = eventList.size
}

