package com.tencent.rl

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.tencent.rl.core.*
import com.tencent.rl.widget.BorderDecoration
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    companion object {
        private const val COLUMNS = Common.SIZE
        private const val ITEM_COUNT = COLUMNS * COLUMNS
        private const val PREF_NAME = "TicTacToe_pref"
        private const val KEY_ENGINE_TYPE = PREF_NAME + "KEY_ENGINE_TYPE"
        private val HUMAN = ChessPieceState.CROSS
    }

    private var dialog: AlertDialog? = null
    private val mainHandler = Handler()
    private val aiEnv1 = AIEnv(Common.AI_PLAYER_1, ChessPieceState.CIRCLE) { index ->
        adapter.update(index, ChessPieceState.CIRCLE)
    }

    /**
     * open this comment to utilize SophisticatedPlayer
     */
//    private val simulatePlayer = SophisticatedPlayer(ChessPieceState.CROSS, aiEnv1, 100) { index, player ->
//        adapter.holders[index]?.itemView?.performClick()
//        if (dialog != null && dialog!!.isShowing) {
//            dialog!!.dismiss()
//            resetGame()
//        }
//    }

    /**
     * open this comment to utilize AIPlayer, which might employ two reinforcement learning algorithms to train each other.
     */
    private val simulatePlayer = AIPlayer(ChessPieceState.CROSS, aiEnv1, 50) { index, player ->
        adapter.holders[index]?.itemView?.performClick()
        if (dialog != null && dialog!!.isShowing) {
            dialog!!.dismiss()
            resetGame()
        }
    }

    private lateinit var group: RadioGroup

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
            (it as Button).let { button ->
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
            aiEnv1.saveQTable(Common.SAVE_DIR_PATH)
        }

        val progressText = findViewById<TextView>(R.id.progress_desc)

        progressText.text = getEpsilonDesc((Common.EPSILON * 100).toInt())
        findViewById<SeekBar>(R.id.epsilon_progress).apply { progress = (Common.EPSILON * 100).toInt() }.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

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

        group = findViewById(R.id.radio_group)

        val engineType = restoreEngineType()
        val btnIdToCheck = when (engineType) {
            AIEnv.EngineType.QLEARNING -> R.id.radio_btn1
            AIEnv.EngineType.SARSA -> R.id.radio_btn2
            AIEnv.EngineType.SARSA_LAMBDA -> R.id.radio_btn3
        }
        group.check(btnIdToCheck)
        aiEnv1.setEngine(engineType)

        group.setOnCheckedChangeListener { _, checkedId ->
            val engineType = when (checkedId) {
                R.id.radio_btn1 -> AIEnv.EngineType.QLEARNING
                R.id.radio_btn2 -> AIEnv.EngineType.SARSA
                R.id.radio_btn3 -> AIEnv.EngineType.SARSA_LAMBDA
                else -> null
            }?.let {
                saveEngineType(it.ordinal)
                aiEnv1.setEngine(it)
            }
        }
    }

    private fun saveEngineType(type: Int) {
        getSharedPreference().edit().apply {
            putInt(KEY_ENGINE_TYPE, type)
        }.apply()
    }

    private fun restoreEngineType(): AIEnv.EngineType {
        val savedType = getSharedPreference().getInt(KEY_ENGINE_TYPE, AIEnv.EngineType.QLEARNING.ordinal)
        return AIEnv.EngineType.values()[savedType]
    }

    private fun getSharedPreference(): SharedPreferences {
        return getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun getEpsilonDesc(progress: Int) = String.format("%s %d %%", getString(R.string.epsilon), progress)

    private fun initEnv() {
        Common.SAVE_DIR_PATH = externalCacheDir.absolutePath
        aiEnv1.loadQTable(Common.SAVE_DIR_PATH)
    }

    private fun resetGame() {
        adapter.reset()
        aiEnv1.reset()
        simulatePlayer.reset()
        // give the ai 50% chance to take the first step
        if (Random.nextFloat() > 0.5f) {
            aiEnv1.updateByMe()
        }
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
                    holder.doAction(HUMAN)
                    val humanAction = Action(holder.index, HUMAN)
                    if (!simulatePlayer.isStarted()) {
                        Log.v(Common.TAG, "HUMAN ===> action=$humanAction")
                    }
                    when (aiEnv1.updateByOpponent(humanAction)) {
                        AIEnv.GameResult.RESULT_DRAW -> showResultDialog(getString(R.string.draw))
                        AIEnv.GameResult.RESULT_CROSS_WIN -> showResultDialog(getString(R.string.win))
                        AIEnv.GameResult.RESULT_CIRCLE_WIN -> showResultDialog(getString(R.string.lose))
                        AIEnv.GameResult.IN_PROGRESS -> Unit
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