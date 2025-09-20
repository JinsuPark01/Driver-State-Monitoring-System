package com.example.android_front.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.android_front.R
import com.example.android_front.model.DispatchResponse

class DispatchPagerAdapter(
    private val items: List<DispatchResponse>,
    private val onItemClick: (Long) -> Unit // dispatchId 전달
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
                    onItemClick(items[position].dispatchId) // dispatchId 전달
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

        // 운행자 이름이 서버에서 없으면 driverId 사용
        holder.tvDriverName.text = "운행자 ID : ${item.username}"
        holder.tvRouteNumber.text = "노선 이름 : ${item.routeNumber}"
        holder.tvDepartureTime.text = "출발 시간 : ${item.scheduledDeparture}"
        holder.tvDriveStatus.text = "상태 : ${item.status.displayName}"
    }

    override fun getItemCount() = items.size
}
