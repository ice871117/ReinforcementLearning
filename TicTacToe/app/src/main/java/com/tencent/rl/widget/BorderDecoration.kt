package com.tencent.rl.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.support.v7.widget.RecyclerView
import android.view.View

class BorderDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val divider: Drawable

    init {
        val attrs = intArrayOf(android.R.attr.listDivider)
        val a = context.obtainStyledAttributes(attrs)
        divider = a.getDrawable(0)
        a.recycle()
    }


    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDraw(c, parent, state)
        drawVertical(c, parent, state)
        drawHorizontal(c, parent, state)
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)
        //画垂直线
        outRect.set(0, 0, divider.intrinsicWidth, 0)
        //画水平线
        outRect.set(0, 0, 0, divider.intrinsicHeight)
    }

    /**
     * 画竖直分割线
     */
    private fun drawVertical(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val childCount = parent.childCount
        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            val params = child.layoutParams as RecyclerView.LayoutParams
            val left = child.right + params.rightMargin
            val top = child.top - params.topMargin
            val right = left + divider.intrinsicWidth
            val bottom = child.bottom + params.bottomMargin
            divider.setBounds(left, top, right, bottom)
            divider.draw(c)
        }
    }

    /**
     * 画水平分割线
     */
    private fun drawHorizontal(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val childCount = parent.childCount
        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            val params = child.layoutParams as RecyclerView.LayoutParams
            val left = child.left - params.leftMargin
            val top = child.bottom + params.bottomMargin
            val right = child.right + params.rightMargin
            val bottom = top + divider.intrinsicHeight
            divider.setBounds(left, top, right, bottom)
            divider.draw(c)
        }
    }

}