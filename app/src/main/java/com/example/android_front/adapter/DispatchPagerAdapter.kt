package com.example.android_front.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.android_front.R
import com.example.android_front.data.DispatchItem

class DispatchPagerAdapter(
    private val items: List<DispatchItem>,
    private val onItemClick: (Long) -> Unit // id만 넘김
) : RecyclerView.Adapter<DispatchPagerAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDriverName: TextView = view.findViewById(R.id.tvDriverName)
        val tvRouteNumber: TextView = view.findViewById(R.id.tvRouteNumber)
        val tvDepartureTime: TextView = view.findViewById(R.id.tvDepartureTime)
        val tvDriveStatus: TextView = view.findViewById(R.id.tvDriveStatus)

        init {
            view.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(items[position].id)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_drive_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvDriverName.text = "운행자 : ${item.driverName}"
        holder.tvRouteNumber.text = "노선 번호 : ${item.routeNumber}"
        holder.tvDepartureTime.text = "출발 시간 : ${item.departureTime}"
        holder.tvDriveStatus.text = "${item.status}"
    }

    override fun getItemCount() = items.size
}

