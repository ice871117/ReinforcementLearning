package com.tencent.rl

import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import com.tencent.rl.core.Action
import com.tencent.rl.core.ChessPieceState
import com.tencent.rl.core.Common
import com.tencent.rl.core.EnvironmentCtrl
import com.tencent.rl.widget.BorderDecoration
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val COLUMNS = Common.SIZE
        private const val ITEM_COUNT = COLUMNS * COLUMNS
    }

    private var dialog: AlertDialog? = null
    private val simulatePlayer = EnvironmentCtrl.RandomPlayer(100) { index ->
        adapter.holders[index]?.itemView?.performClick()
        if (dialog != null && dialog!!.isShowing) {
            dialog!!.dismiss()
            resetGame()
        }
    }

    private val adapter: MyAdapter by lazy {
        MyAdapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initUI()
        initEnv()
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.dismiss()
    }

    private fun initUI() {

        findViewById<Button>(R.id.restart_btn).setOnClickListener {
            resetGame()
        }

        findViewById<Button>(R.id.train_btn).setOnClickListener {
            (it as Button).let {
                button ->
                if (!simulatePlayer.isStarted()) {
                    simulatePlayer.start()
                    button.setText(R.string.stop_train)
                } else {
                    simulatePlayer.stop()
                    button.setText(R.string.train)
                }
            }
        }

        findViewById<Button>(R.id.save_btn).setOnClickListener {
            EnvironmentCtrl.saveQTable(Common.SAVE_PATH)
        }

        val progressText = findViewById<TextView>(R.id.progress_desc)

        progressText.text = getEpsilonDesc((Common.EPSILON * 100).toInt())
        findViewById<SeekBar>(R.id.epsilon_progress).apply { progress = (Common.EPSILON * 100).toInt() }.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                progressText.text = getEpsilonDesc(progress)
                Common.EPSILON = progress.toFloat() / 100f
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

        })

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = GridLayoutManager(this@MainActivity, COLUMNS)
        recyclerView.addItemDecoration(BorderDecoration(this@MainActivity))
        recyclerView.adapter = adapter
    }

    private fun getEpsilonDesc(progress: Int) = String.format("%s %d %%", getString(R.string.epsilon),  progress)

    private fun initEnv() {
        Common.SAVE_PATH = externalCacheDir.absolutePath + File.separator + "model.rl"
        EnvironmentCtrl.loadQTable(Common.SAVE_PATH)
        EnvironmentCtrl.updateListener = { index ->
            adapter.update(index, Common.AI)
        }
    }

    private fun resetGame() {
        adapter.reset()
        EnvironmentCtrl.reset()
    }

    private fun showResultDialog(msg: String) {
        if (dialog == null) {
            dialog = AlertDialog.Builder(this@MainActivity)
                    .setPositiveButton(R.string.ok) { dialog, _ ->
                        dialog.dismiss()
                        resetGame()
                    }
                    .setCancelable(false)
                    .create()
        }
        dialog?.setMessage(msg)
        dialog?.show()
    }

    inner class MyAdapter : RecyclerView.Adapter<VH>() {

        internal val holders = HashMap<Int, VH>()

        override fun onCreateViewHolder(parent: ViewGroup, position: Int): VH {
            val itemView = View(parent.context)
            val itemSize = parent.context.resources.displayMetrics.widthPixels / COLUMNS
            itemView.layoutParams = RecyclerView.LayoutParams(itemSize, itemSize)
            return VH(itemView)
        }

        override fun getItemCount() = ITEM_COUNT

        override fun onBindViewHolder(holder: VH, position: Int) {
            holders[position] = holder
            holder.index = position
            holder.itemView.setOnClickListener {
                if (holder.state == ChessPieceState.NONE) {
                    holder.doAction(Common.HUMAN)
                    when (EnvironmentCtrl.updateByHuman(Action(holder.index, Common.HUMAN))) {
                        EnvironmentCtrl.GameResult.RESULT_DRAW -> showResultDialog(getString(R.string.draw))
                        EnvironmentCtrl.GameResult.RESULT_HUMAN_WIN -> showResultDialog(getString(R.string.win))
                        EnvironmentCtrl.GameResult.RESULT_HUMAN_LOSE -> showResultDialog(getString(R.string.lose))
                        EnvironmentCtrl.GameResult.IN_PROGRESS -> Unit
                    }
                }
            }
        }

        fun update(index: Int, chessState: ChessPieceState) {
            holders[index]?.doAction(chessState)
        }

        fun reset() = holders.values.forEach { vh -> vh.clear() }

    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var state = ChessPieceState.NONE
        var index = 0

        fun clear() {
            itemView.background = null
            state = ChessPieceState.NONE
        }

        fun doAction(chessState: ChessPieceState) {
            if (state != ChessPieceState.NONE) {
                throw IllegalStateException("Already has a chess state with $state")
            }
            state = chessState
            when (chessState) {
                ChessPieceState.CROSS -> cross()
                ChessPieceState.CIRCLE -> circle()
            }
        }

        fun cross() {
            itemView.setBackgroundResource(R.mipmap.cross)
        }

        fun circle() {
            itemView.setBackgroundResource(R.mipmap.circle)
        }

    }

}