package com.tencent.rl.core

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.tencent.rl.MyApplication
import com.tencent.rl.R
import java.io.*
import java.util.concurrent.Executors

object EnvironmentCtrl {

    enum class GameResult {
        RESULT_DRAW, RESULT_HUMAN_LOSE, RESULT_HUMAN_WIN, IN_PROGRESS
    }

    enum class EngineType {
        QLEARNING, SARSA, SARSA_LAMBDA
    }

    @Volatile
    internal var qTable = QTable()
    private lateinit var impl: AbsEnvironmentImpl
    private val threadPool = Executors.newFixedThreadPool(1)
    var updateListener: ((index: Int) -> Unit)? = null

    fun changeEngine(newEngine: EngineType) {
        impl = when (newEngine) {
            EngineType.QLEARNING -> QLearningEnvironmentImpl()
            EngineType.SARSA -> SarsaEnvironmentImpl()
            EngineType.SARSA_LAMBDA -> SarsaLambdaEnvironmentImpl()
        }
        Toast.makeText(MyApplication.GlobalContext, "Change Engine to ${newEngine.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
    }

    fun updateByHuman(action: Action) = impl.updateByHuman(action)

    fun reset() {
        Log.w(Common.TAG, "Game reset!")
        impl.reset()
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
                    qTable.selfCheck()
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
            doWhenPlay(play(impl.currBoardState))
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
            return WinDetector.findBestStep(impl.currBoardState, Common.HUMAN) ?: state.availableActionIndexes().random()
        }
    }
}

abstract class AbsEnvironmentImpl {

    internal var currBoardState = BoardState(Common.SIZE)

    abstract fun updateByHuman(action: Action): EnvironmentCtrl.GameResult

    abstract fun updateByAI(): EnvironmentCtrl.GameResult

    protected abstract val engine: IRLEngine

    open fun reset() {
        currBoardState = BoardState(Common.SIZE)
    }

}

class QLearningEnvironmentImpl : AbsEnvironmentImpl() {

    override val engine: IRLEngine
        get() = QLearningTicTacToeEngine()

    override fun updateByHuman(action: Action): EnvironmentCtrl.GameResult {
        Log.v(Common.TAG, "HUMAN ===> action=$action")
        val tempState = currBoardState.mutate(action)
        if (tempState != null) {
            currBoardState = tempState
            return when {
                currBoardState.getWinner() == Common.HUMAN -> EnvironmentCtrl.GameResult.RESULT_HUMAN_WIN
                currBoardState.isFull() -> EnvironmentCtrl.GameResult.RESULT_DRAW
                else -> updateByAI()
            }
        } else {
            return EnvironmentCtrl.GameResult.IN_PROGRESS
        }
    }

    override fun updateByAI(): EnvironmentCtrl.GameResult {
        val action = engine.chooseAction(EnvironmentCtrl.qTable, currBoardState)
        val feedback = engine.getFeedBack(currBoardState, action)
        // for qlearning or sarsa when AI win
        engine.doLearning(EnvironmentCtrl.qTable, currBoardState, action, feedback.first, feedback.second, null)
        currBoardState = feedback.first
        EnvironmentCtrl.updateListener?.invoke(action.index)
        return when {
            currBoardState.getWinner() == Common.AI -> EnvironmentCtrl.GameResult.RESULT_HUMAN_LOSE
            currBoardState.isFull() -> EnvironmentCtrl.GameResult.RESULT_DRAW
            else -> EnvironmentCtrl.GameResult.IN_PROGRESS
        }
    }

}

open class SarsaEnvironmentImpl : AbsEnvironmentImpl() {

    override val engine: IRLEngine
        get() = SarsaTicTacToeEngine()

    private var lastStateForSarsa: SarsaTicTacToeEngine.LastLerningState? = null

    override fun updateByHuman(action: Action): EnvironmentCtrl.GameResult {
        Log.v(Common.TAG, "HUMAN ===> action=$action")
        val tempState = currBoardState.mutate(action)
        if (tempState != null) {
            currBoardState = tempState
            return when {
                currBoardState.getWinner() == Common.HUMAN -> {
                    learnForSarsa(null)
                    EnvironmentCtrl.GameResult.RESULT_HUMAN_WIN
                }
                currBoardState.isFull() -> EnvironmentCtrl.GameResult.RESULT_DRAW
                else -> updateByAI()
            }
        } else {
            return EnvironmentCtrl.GameResult.IN_PROGRESS
        }
    }

    override fun updateByAI(): EnvironmentCtrl.GameResult {
        val action = engine.chooseAction(EnvironmentCtrl.qTable, currBoardState)
        learnForSarsa(action)
        val feedback = engine.getFeedBack(currBoardState, action)
        // remember as last state
        lastStateForSarsa = SarsaTicTacToeEngine.LastLerningState(currBoardState, action, feedback.first, feedback.second)
        // for qlearning or sarsa when AI win
        engine.doLearning(EnvironmentCtrl.qTable, currBoardState, action, feedback.first, feedback.second, null)
        currBoardState = feedback.first
        EnvironmentCtrl.updateListener?.invoke(action.index)
        return when {
            currBoardState.getWinner() == Common.AI -> EnvironmentCtrl.GameResult.RESULT_HUMAN_LOSE
            currBoardState.isFull() -> EnvironmentCtrl.GameResult.RESULT_DRAW
            else -> EnvironmentCtrl.GameResult.IN_PROGRESS
        }
    }

    private fun learnForSarsa(action: Action?) {
        lastStateForSarsa?.apply {
            engine.doLearning(EnvironmentCtrl.qTable, currState, currAction, nextState, reward, action)
        }
    }

    override fun reset() {
        super.reset()
        lastStateForSarsa = null
    }

}

class SarsaLambdaEnvironmentImpl : SarsaEnvironmentImpl() {

    override val engine: IRLEngine
        get() = SarsaLambdaTicTacToeEngine()

    override fun reset() {
        super.reset()
        (engine as SarsaLambdaTicTacToeEngine).reset()
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