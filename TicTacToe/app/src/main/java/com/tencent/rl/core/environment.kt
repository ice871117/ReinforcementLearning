package com.tencent.rl.core

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.tencent.rl.MyApplication
import com.tencent.rl.R
import java.io.*
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.Executors

object EnvironmentCtrl {

    enum class GameResult {
        RESULT_DRAW, RESULT_HUMAN_LOSE, RESULT_HUMAN_WIN, IN_PROGRESS
    }

    @Volatile
    private var qTable = QTable()
    private var currBoardState = BoardState(Common.SIZE)
    private var engine: IRLEngine = QLearningTicTacToeEngine()
    private val threadPool = Executors.newFixedThreadPool(1)
    var updateListener: ((index: Int) -> Unit)? = null
    private var lastStateForSarsa: SarsaTicTacToeEngine.LastLerningState? = null

    fun changeEngine(newEngine: IRLEngine) {
        engine = newEngine
        Toast.makeText(MyApplication.GlobalContext, "Change Engine to ${newEngine.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
    }

    fun updateByHuman(action: Action): GameResult {
        Log.v(Common.TAG, "HUMAN ===> action=$action")
        val tempState = currBoardState.mutate(action)
        if (tempState != null) {
            currBoardState = tempState
            return when {
                currBoardState.getWinner() == Common.HUMAN ->  {
                    learnForSarsa(null)
                    GameResult.RESULT_HUMAN_WIN
                }
                currBoardState.isFull() -> GameResult.RESULT_DRAW
                else -> updateByAI()
            }
        } else {
            return GameResult.IN_PROGRESS
        }
    }

    private fun updateByAI(): GameResult {
        val action = engine.chooseAction(qTable, currBoardState)
        learnForSarsa(action)
        val feedback = engine.getFeedBack(currBoardState, action)
        // remember as last state
        lastStateForSarsa = SarsaTicTacToeEngine.LastLerningState(currBoardState, action, feedback.first, feedback.second)
        // for qlearning or sarsa when AI win
        engine.doLearning(qTable, currBoardState, action, feedback.first, feedback.second, null)
        currBoardState = feedback.first
        updateListener?.invoke(action.index)
        return when {
            currBoardState.getWinner() == Common.AI -> GameResult.RESULT_HUMAN_LOSE
            currBoardState.isFull() -> GameResult.RESULT_DRAW
            else -> GameResult.IN_PROGRESS
        }
    }

    private fun learnForSarsa(action: Action?) {
        if (engine is SarsaTicTacToeEngine) {
            lastStateForSarsa?.apply {
                engine.doLearning(qTable, currState, currAction, nextState, reward, action)
            }
        }
    }

    fun reset() {
        Log.w(Common.TAG, "Game reset!")
        currBoardState = BoardState(Common.SIZE)
        lastStateForSarsa = null
    }

    fun saveQTable(path: String?) {
        if (path == null) {
            Log.w(Common.TAG, "saveQTable path is null")
            return
        }
        threadPool.submit {
            var baos: ByteArrayOutputStream? = null
            var fos: BufferedOutputStream? = null
            try {
                fos = BufferedOutputStream(FileOutputStream(path))
                baos = ByteArrayOutputStream()
                val objectOutputStream = ObjectOutputStream(baos)
                objectOutputStream.writeObject(qTable.clone())
                fos.write(baos.toByteArray())
                Log.i(Common.TAG, "saveQTable complete")
                Common.runOnUIThread {
                    Toast.makeText(MyApplication.GlobalContext, R.string.saved, Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Log.w(Common.TAG, "saveQTable failed, due to ${e.localizedMessage}", e)
            } finally {
                Common.closeSilently(baos)
                Common.closeSilently(fos)
            }
        }
    }

    fun loadQTable(path: String?) {
        if (path == null) {
            Log.w(Common.TAG, "loadQTable path is null")
            return
        }
        val file = File(path)
        if (file.exists()) {
            threadPool.submit {
                var bais: ByteArrayInputStream? = null
                var fis: BufferedInputStream? = null
                try {
                    fis = BufferedInputStream(FileInputStream(path))
                    val buffer = ByteArray(file.length().toInt())
                    val readCount = fis.read(buffer)
                    if (readCount != buffer.size) {
                        throw IllegalStateException("read count($readCount) is smaller than file size(${file.length()})")
                    }
                    bais = ByteArrayInputStream(buffer)
                    val objectInputStream = ObjectInputStream(bais)
                    qTable = objectInputStream.readObject() as QTable
                    Log.i(Common.TAG, "loadQTable complete")
                    Common.runOnUIThread {
                        Toast.makeText(MyApplication.GlobalContext, R.string.loaded, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: IOException) {
                    Log.w(Common.TAG, "loadQTable failed, due to ${e.localizedMessage}", e)
                } finally {
                    Common.closeSilently(bais)
                    Common.closeSilently(fis)
                }
            }
        }
    }

    interface SimulatePlayer : Runnable {

        fun play(state: BoardState): Int

        fun start()

        fun stop()

        fun isStarted(): Boolean
    }

    abstract class AbsPlayer(private val interval: Long, private val doWhenPlay: (Int) -> Unit) : SimulatePlayer {

        private val handler = Handler(Looper.getMainLooper())
        private var started = false

        override fun run() {
            doWhenPlay(play(EnvironmentCtrl.currBoardState))
            handler.postDelayed(this, interval)
        }

        override fun start() {
            handler.post(this)
            started = true
        }

        override fun stop() {
            handler.removeCallbacks(this)
            started = false
        }

        override fun isStarted() = started

    }

    class RandomPlayer(interval: Long, doWhenPlay: (Int) -> Unit) : AbsPlayer(interval, doWhenPlay) {
        override fun play(state: BoardState): Int {
            return WinDetector.findBestStep(currBoardState, Common.HUMAN) ?: state.availableActionIndexes().random()
        }
    }

}


class QTable : Cloneable, Serializable {

    companion object {
        @JvmStatic
        private val serialVersionUID = 1L
    }

    private var table = HashMap<BoardState, FloatArray>()

    private fun buildActionArray(): FloatArray = FloatArray(columns()) { 0f }

    fun columns(): Int = Common.BOARD_TOTAL_SIZE

    fun getTable(state: BoardState): FloatArray {
        var tableForState = table[state]
        if (tableForState == null) {
            tableForState = buildActionArray()
            synchronized(this@QTable) {
                table[state] = tableForState
            }
        }
        return tableForState
    }

    fun queryMax(state: BoardState): Int {
        val tableForState = table[state]
        return if (tableForState == null) {
            val newTable = buildActionArray()
            synchronized(this@QTable) {
                table[state] = newTable
            }
            findMaxIndex(newTable, state.availableActionIndexes())
        } else {
            findMaxIndex(tableForState, state.availableActionIndexes())
        }
    }

    private fun findMaxIndex(array: FloatArray, availableIndexes: List<Int>): Int {
        val max = array.filterIndexed { index, _ -> index in availableIndexes }.max()
        val result = mutableListOf<Int>()
        array.forEachIndexed { index, value ->
            if (value >= max!!) {
                result.add(index)
            }
        }
        // use random in case that some columns has the same value
        return result.intersect(availableIndexes).random()
    }

    private fun writeObject(outputStream: ObjectOutputStream) {
        synchronized(this@QTable) {
            outputStream.writeObject(table)
        }
    }

    private fun readObject(inputStream: ObjectInputStream) {
        table = (inputStream.readObject() as? HashMap<BoardState, FloatArray>) ?: HashMap()
    }

    public override fun clone(): QTable {
        val ret = QTable()
        for ((index, value) in table) {
            ret.table[index] = Arrays.copyOf(value, value.size)
        }
        return ret
    }
}


object WinDetector {

    val VERTICAL_STRATEGY = OrthogonalStrategy(0)
    val HORIZONTAL_STRATEGY = OrthogonalStrategy(1)
    val DIAGONAL1 = DiagonalStrategy(0)
    val DIAGONAL2 = DiagonalStrategy(1)

    enum class Direction(val strategy: DetectStrategy) {
        HORIZONTAL(VERTICAL_STRATEGY),
        VERTICAL(HORIZONTAL_STRATEGY),
        DIAGONAL_LEFT_TOP_TO_RIGHT_BOTTOM(DIAGONAL1),
        DIAGONAL_RIGHT_TOP_LEFT_BOTTOM(DIAGONAL2)
    }

    fun hasWon(chessboard: BoardState, targetState: ChessPieceState) = Direction.values().any {
        it.strategy.detectWinner(chessboard, targetState)
    }

    fun oneMoreStepToWin(chessboard: BoardState, targetState: ChessPieceState) = Direction.values().any {
        it.strategy.oneMoreStepToWin(chessboard, targetState)
    }

    fun findBestStep(chessboard: BoardState, targetState: ChessPieceState): Int? {
        Direction.values().forEach {
            val step = it.strategy.findBestStep(chessboard, targetState)
            if (step != null) {
                return step
            }
        }
        return null
    }

}

interface DetectStrategy {
    fun detectWinner(chessboard: BoardState, targetState: ChessPieceState): Boolean
    fun oneMoreStepToWin(chessboard: BoardState, targetState: ChessPieceState): Boolean
    fun findBestStep(chessboard: BoardState, targetState: ChessPieceState): Int?
}

abstract class AbsStrategy : DetectStrategy {
    override fun detectWinner(chessboard: BoardState, targetState: ChessPieceState): Boolean {
        return detect(chessboard, targetState, chessboard.size)
    }

    override fun oneMoreStepToWin(chessboard: BoardState, targetState: ChessPieceState): Boolean {
        return detect(chessboard, targetState, chessboard.size - 1)
    }

    override fun findBestStep(chessboard: BoardState, targetState: ChessPieceState): Int? {
        val resultWrapper = ResultWrapper<Int>()
        if (detect(chessboard, targetState, chessboard.size - 1, resultWrapper)) {
            return resultWrapper.result
        }
        return null
    }

    protected abstract fun detect(chessboard: BoardState, targetState: ChessPieceState, expect: Int, detectResult: ResultWrapper<Int>? = null): Boolean
}

class ResultWrapper<T> {
    var result: T? = null
}

/**
 * check if winning in horizontal or vertical direction
 * @param axis 0 means vertical and 1 means horizontal
 */
class OrthogonalStrategy(private val axis: Int) : AbsStrategy() {

    override fun detect(chessboard: BoardState, targetState: ChessPieceState, expect: Int, detectResult: ResultWrapper<Int>?): Boolean {
        for (i in 0 until chessboard.size) {
            var collector = 0
            var possibleIndex = 0
            for (j in 0 until chessboard.size) {
                when (if (axis == 0) chessboard.get(j, i) else chessboard.get(i, j)) {
                    targetState -> collector++
                    ChessPieceState.NONE ->
                        // toVerify is ChessPieceState.NONE
                        possibleIndex = if (axis == 0) Common.coord2actionIndex(j, i) else Common.coord2actionIndex(i, j)
                    else -> collector-- // for testing one more step to win
                }
            }
            if (collector == expect) {
                detectResult?.result = possibleIndex
                Log.i(Common.TAG, "OrthogonalStrategy detected, axis=$axis state=$targetState expect=$expect")
                return true
            }
        }
        return false
    }

}

/**
 * @param direction 0 means left-top to right-bottom
 *                  whereas 1 means right-top to left-bottom
 */
class DiagonalStrategy(private val direction: Int) : AbsStrategy() {

    override fun detect(chessboard: BoardState, targetState: ChessPieceState, expect: Int, detectResult: ResultWrapper<Int>?): Boolean {
        var collector = 0
        var possibleIndex = 0
        val size = chessboard.size
        var i = 0
        var j = if (direction == 0) 0 else size - 1
        do {
            when (chessboard.get(i, j)) {
                targetState -> collector++
                ChessPieceState.NONE ->
                    // toVerify is ChessPieceState.NONE
                    possibleIndex = Common.coord2actionIndex(i, j)
                else -> collector-- // for testing one more step to win
            }
        } while (if (direction == 0) {
                    ++i < size && ++j < size
                } else {
                    ++i < size && --j >= 0
                })
        if (collector == expect) {
            detectResult?.result = possibleIndex
            Log.i(Common.TAG, "DiagonalStrategy detected, direction=$direction state=$targetState expect=$expect")
            return true
        }
        return false
    }
}