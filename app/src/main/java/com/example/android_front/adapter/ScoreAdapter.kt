package com.example.android_front.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.android_front.R
import com.example.android_front.data.ScoreItem

class ScoreAdapter(private val scores: List<ScoreItem>) :
    RecyclerView.Adapter<ScoreAdapter.ScoreViewHolder>() {

    inner class ScoreViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLabel: TextView = view.findViewById(R.id.tvScoreLabel)
        val tvValue: TextView = view.findViewById(R.id.tvScoreValue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScoreViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_score_card, parent, false)
        return ScoreViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScoreViewHolder, position: Int) {
        val item = scores[position]
        holder.tvLabel.text = item.label
        holder.tvValue.text = item.value
        holder.tvValue.setTextColor(item.color)
    }

    override fun getItemCount() = scores.size
}
