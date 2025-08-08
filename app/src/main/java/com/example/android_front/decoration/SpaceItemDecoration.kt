package com.example.android_front.decoration

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SpaceItemDecoration(private val space: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val itemCount = state.itemCount

        // GridLayoutManager의 spanCount 가져오기
        val spanCount = (parent.layoutManager as? GridLayoutManager)?.spanCount ?: 1

        // 마지막 줄인지 확인
        val isLastRow = position >= itemCount - spanCount

        if (!isLastRow) {
            outRect.bottom = space // 마지막 줄이 아니면 하단 간격
        }
    }
}
