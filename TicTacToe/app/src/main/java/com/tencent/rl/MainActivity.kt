package com.tencent.rl

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.tencent.rl.widget.BorderDecoration

class MainActivity: AppCompatActivity() {

    companion object {
        private const val COLUMNS = 3
        private const val ITEM_COUNT = COLUMNS * COLUMNS
    }

    private val adapter: MyAdapter by lazy {
        MyAdapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initUI()
    }

    private fun initUI() {

        findViewById<Button>(R.id.restart_btn).setOnClickListener {
            adapter.reset()
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = GridLayoutManager(this@MainActivity, COLUMNS)
        recyclerView.addItemDecoration(BorderDecoration(this@MainActivity))
        recyclerView.adapter = adapter
    }

    class MyAdapter: RecyclerView.Adapter<VH>() {

        private val holders = HashSet<VH>()

        override fun onCreateViewHolder(parent: ViewGroup, position: Int): VH {
            val itemView = View(parent.context)
            val itemSize = parent.context.resources.displayMetrics.widthPixels / COLUMNS
            itemView.layoutParams = RecyclerView.LayoutParams(itemSize, itemSize)
            return VH(itemView).also { holders.add(it) }
        }

        override fun getItemCount() = ITEM_COUNT

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.itemView.setOnClickListener {
                holder.cross()
            }
        }

        fun reset() = holders.forEach { it.clear() }
    }

    class VH(itemView: View): RecyclerView.ViewHolder(itemView) {

        fun clear() {
            itemView.background = null
        }

        fun cross() {
            itemView.setBackgroundResource(R.mipmap.cross)
        }

        fun circle() {
            itemView.setBackgroundResource(R.mipmap.circle)
        }

    }

}