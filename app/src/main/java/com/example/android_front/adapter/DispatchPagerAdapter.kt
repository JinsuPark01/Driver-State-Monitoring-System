package com.example.android_front.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.android_front.R
import com.example.android_front.model.DispatchDetailResponse
import com.example.android_front.model.DispatchStatus

class DispatchPagerAdapter(
    private val items: List<DispatchDetailResponse>,
    private val onItemClick: (DispatchDetailResponse) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_EMPTY = 1
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvVehicleNumber: TextView = view.findViewById(R.id.tvVehicleNumber)
        val tvRouteNumber: TextView = view.findViewById(R.id.tvRouteNumber)
        val tvDepartureTime: TextView = view.findViewById(R.id.tvDepartureTime)
        val tvDriveStatus: TextView = view.findViewById(R.id.tvDriveStatus)

        init {
            view.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(items[position])
                }
            }
        }
    }

    inner class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNoDispatch: TextView = view.findViewById(R.id.tvNoDispatch)
    }

    override fun getItemViewType(position: Int): Int {
        return if (items.isEmpty()) VIEW_TYPE_EMPTY else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_EMPTY) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_drive_no_card, parent, false)
            EmptyViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_drive_card, parent, false)
            ViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolder) {
            val item = items[position]

            holder.tvVehicleNumber.text = "  (${item.vehicleNumber ?: "알 수 없음"})"
            holder.tvRouteNumber.text = "${item.routeNumber ?: "-"}번"
            holder.tvDepartureTime.text = "${item.dispatchDate.replace("-", ".")} | ${item.scheduledDepartureTime.substringAfter("T").substring(0, 5)} ~ ${item.scheduledArrivalTime.substringAfter("T").substring(0, 5)}"
            holder.tvDriveStatus.text = "${item.status.displayName ?: "-"}"

            // 상태별 색상 변경
            val colorRes = when(item.status) {
                DispatchStatus.SCHEDULED -> R.color.blue       // 운행 전
                DispatchStatus.COMPLETED -> R.color.green      // 완료
                DispatchStatus.CANCELED -> R.color.red         // 취소
                else -> R.color.gray                            // 기타
            }

            holder.tvDriveStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, colorRes))

            // next_action 업데이트
            holder.itemView.findViewById<TextView>(R.id.tv_next_action).text = when (item.status) {
                DispatchStatus.SCHEDULED -> "운행 시작"
                DispatchStatus.COMPLETED -> "결과 확인"
                DispatchStatus.CANCELED -> "배차 취소"
                else -> "운행 중"
            }
        } else if (holder is EmptyViewHolder) {
            holder.tvNoDispatch.text = "배차 일정이 없습니다"
        }
    }

    override fun getItemCount(): Int {
        return if (items.isEmpty()) 1 else items.size
    }
}
