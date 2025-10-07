package com.example.android_front.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.android_front.R
import com.example.android_front.model.NotificationResponse
import com.example.android_front.model.NotificationType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NotificationAdapter(
    private val items: List<NotificationResponse>
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDispatchDate: TextView = itemView.findViewById(R.id.tvDispatchDate)
        val tvIsRead: TextView = itemView.findViewById(R.id.tvNotificationIsRead)
        val tvCreatedAt: TextView = itemView.findViewById(R.id.tvNotificationCreatedAt)
        val tvMessage: TextView = itemView.findViewById<TextView>(R.id.tvNotificationMessage)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm_card, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.tvDispatchDate.text = item.payload?.scheduledDepartureTime?.let { dateStr ->
            try {
                // 0~16까지 자르고 T를 공백으로 변경
                val trimmed = dateStr.substring(0, 16).replace("T", " ")
                // "2025-09-30 09:00" → "25/9/30 09:00" 포맷
                val parts = trimmed.split(" ")
                val dateParts = parts[0].split("-")
                val formattedDate = "${dateParts[0].substring(2)}/${dateParts[1].toInt()}/${dateParts[2].toInt()} ${parts[1]}"
                formattedDate
            } catch (e: Exception) {
                dateStr // 파싱 실패하면 원래 문자열 그대로
            }
        } ?: ""


        if (item.isRead) {
            holder.tvIsRead.text = "읽음"
            holder.tvIsRead.setTextColor(holder.itemView.context.getColor(R.color.blue))
        } else {
            holder.tvIsRead.text = "미읽음"
            holder.tvIsRead.setTextColor(holder.itemView.context.getColor(R.color.red))
        }

        holder.tvCreatedAt.text = item.createdAt?.substring(0,16)?.replace("T"," ") ?: ""

        // ✅ notificationType에 따른 메시지 설정
        holder.tvMessage.text = when(item.notificationType) {
            NotificationType.NEW_DISPATCH_ASSIGNED -> "에 신규 배차 추가"
            NotificationType.DISPATCH_CANCELED -> " 배차 취소"
        }
    }
}

