package com.example.android_front.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.android_front.R
import com.example.android_front.model.UserDetailResponse

class ScoreAdapter(private val userDetail: UserDetailResponse) :
    RecyclerView.Adapter<ScoreAdapter.ScoreViewHolder>() {

    inner class ScoreViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLabel: TextView = view.findViewById(R.id.tvScoreLabel)
        val tvValue: TextView = view.findViewById(R.id.tvScoreValue)
    }

    private val labels = listOf(
        "종합점수",
        "졸음운전",
        "급가속",
        "급제동",
        "이상행동"
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScoreViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_score_card, parent, false)
        return ScoreViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScoreViewHolder, position: Int) {
        holder.tvLabel.text = labels[position]
        val value = when (position) {
            0 -> userDetail.avgDrivingScore ?: 100.0
            1 -> userDetail.avgDrowsinessCount ?: 0.0
            2 -> userDetail.avgAccelerationCount ?: 0.0
            3 -> userDetail.avgBrakingCount ?: 0.0
            4 -> userDetail.avgAbnormalCount ?: 0.0
            else -> 0.0
        }
        holder.tvValue.text = value.toString()
    }

    override fun getItemCount() = 5
}

