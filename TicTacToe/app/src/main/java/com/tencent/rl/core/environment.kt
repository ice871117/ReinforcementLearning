package com.tencent.rl.core

import android.util.Log
import android.widget.Toast
import com.tencent.rl.MyApplication
import com.tencent.rl.R
import com.tencent.rl.core.Common.TAG
import java.io.*
import java.util.concurrent.Executors

class AIEnv(private val myName: String, val myChess: ChessPieceState, private val updateListener: ((index: Int) -> Unit)?) {

    enum class GameResult {
        RESULT_DRAW, RESULT_CIRCLE_WIN, RESULT_CROSS_WIN, IN_PROGRESS
    }

    enum class EngineType {
        QLEARNING, SARSA, SARSA_LAMBDA
    }

    @Volatile
    internal var qTable = QTable()
    private lateinit var impl: AbsEnvironmentImpl
    private val threadPool = Executors.newFixedThreadPool(1)

    fun setEngine(newEngine: EngineType) {
        val opponentChess = Common.getOpponentChess(myChess)
        impl = when (newEngine) {
            EngineType.QLEARNING -> QLearningEnvironmentImpl(myName, myChess, opponentChess, qTable, updateListener)
            EngineType.SARSA -> SarsaEnvironmentImpl(myName, myChess, opponentChess, qTable, updateListener)
            EngineType.SARSA_LAMBDA -> SarsaLambdaEnvironmentImpl(myName, myChess, opponentChess, qTable, updateListener)
        }
        Toast.makeText(MyApplication.GlobalContext, "$myName Set Engine to $newEngine", Toast.LENGTH_SHORT).show()
    }

    fun updateByOpponent(action: Action) = impl.updateByOpponent(action)

    fun updateByMe() = impl.updateByMe()

    fun getCurrentState(): BoardState {
        return AbsEnvironmentImpl.CURR_BOARD_STATE
    }

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
                val file = File(path, "${myName}_model.rl")
                fos = BufferedOutputStream(FileOutputStream(file))
                baos = ByteArrayOutputStream()
                val objectOutputStream = ObjectOutputStream(baos)
                objectOutputStream.writeObject(qTable.clone())
                fos.write(baos.toByteArray())
                Log.i(Common.TAG, "saveQTable complete")
                Common.runOnUIThread {
                    Toast.makeText(MyApplication.GlobalContext, MyApplication.GlobalContext.resources.getString(R.string.saved, myName), Toast.LENGTH_SHORT).show()
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
        val file = File(path, "${myName}_model.rl")
        if (file.exists()) {
            threadPool.submit {
                var bais: ByteArrayInputStream? = null
                var fis: BufferedInputStream? = null
                try {
                    fis = BufferedInputStream(FileInputStream(file.absoluteFile))
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
                        Toast.makeText(MyApplication.GlobalContext, MyApplication.GlobalContext.resources.getString(R.string.loaded, myName), Toast.LENGTH_SHORT).show()
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

}


abstract class AbsEnvironmentImpl(val name: String, val myChess: ChessPieceState, val opponentChess: ChessPieceState, val qTable: QTable, val updateListener: ((index: Int) -> Unit)?) {

    companion object {
        internal var CURR_BOARD_STATE = BoardState(Common.SIZE)
    }

    abstract fun updateByOpponent(action: Action): AIEnv.GameResult

    abstract fun updateByMe(): AIEnv.GameResult

    protected abstract val engine: IRLEngine

    open fun reset() {
        CURR_BOARD_STATE = BoardState(Common.SIZE)
    }

    protected fun getGameResultByWinner(chess: ChessPieceState): AIEnv.GameResult {
        return when (chess) {
            ChessPieceState.CROSS -> AIEnv.GameResult.RESULT_CROSS_WIN
            ChessPieceState.CIRCLE -> AIEnv.GameResult.RESULT_CIRCLE_WIN
            else -> throw java.lang.IllegalStateException("$chess is not a valid winner!")
        }
    }
}


class QLearningEnvironmentImpl(name: String, myChess: ChessPieceState, opponentChess: ChessPieceState, qTable: QTable, updateListener: ((index: Int) -> Unit)?)
    : AbsEnvironmentImpl(name, myChess, opponentChess, qTable, updateListener) {

    override val engine: IRLEngine
        get() = QLearningTicTacToeEngine()

    override fun updateByOpponent(action: Action): AIEnv.GameResult {
        val tempState = CURR_BOARD_STATE.mutate(action)
        if (tempState != null) {
            CURR_BOARD_STATE = tempState
        }
        return when {
            CURR_BOARD_STATE.getWinner() == opponentChess -> getGameResultByWinner(opponentChess)
            CURR_BOARD_STATE.isFull() -> AIEnv.GameResult.RESULT_DRAW
            else -> updateByMe()
        }
    }

    override fun updateByMe(): AIEnv.GameResult {
        val action = engine.chooseAction(qTable, CURR_BOARD_STATE, myChess)
        val feedback = engine.step(CURR_BOARD_STATE, action)
        // for qlearning or sarsa when AI win
        engine.doLearning(qTable, CURR_BOARD_STATE, action, feedback.first, feedback.second, null)
        CURR_BOARD_STATE = feedback.first
        updateListener?.invoke(action.index)
        Log.i(TAG, "$name's action = $action")
        return when {
            CURR_BOARD_STATE.getWinner() == myChess -> getGameResultByWinner(myChess)
            CURR_BOARD_STATE.isFull() -> AIEnv.GameResult.RESULT_DRAW
            else -> AIEnv.GameResult.IN_PROGRESS
        }
    }

}

open class SarsaEnvironmentImpl(name: String, myChess: ChessPieceState, opponentChess: ChessPieceState, qTable: QTable, updateListener: ((index: Int) -> Unit)?)
    : AbsEnvironmentImpl(name, myChess, opponentChess, qTable, updateListener) {

    override val engine: IRLEngine
        get() = SarsaTicTacToeEngine()

    private var lastStateForSarsa: SarsaTicTacToeEngine.LastLerningState? = null

    override fun updateByOpponent(action: Action): AIEnv.GameResult {
        val tempState = CURR_BOARD_STATE.mutate(action)
        if (tempState != null) {
            CURR_BOARD_STATE = tempState
        }
        return when {
            CURR_BOARD_STATE.getWinner() == opponentChess -> {
                learnForSarsa(null)
                getGameResultByWinner(opponentChess)
            }
            CURR_BOARD_STATE.isFull() -> AIEnv.GameResult.RESULT_DRAW
            else -> updateByMe()
        }
    }

    override fun updateByMe(): AIEnv.GameResult {
        val action = engine.chooseAction(qTable, CURR_BOARD_STATE, myChess)
        learnForSarsa(action)
        val feedback = engine.step(CURR_BOARD_STATE, action)
        // remember as last state
        lastStateForSarsa = SarsaTicTacToeEngine.LastLerningState(CURR_BOARD_STATE, action, feedback.first, feedback.second)
        // for qlearning or sarsa when AI win
        engine.doLearning(qTable, CURR_BOARD_STATE, action, feedback.first, feedback.second, null)
        CURR_BOARD_STATE = feedback.first
        updateListener?.invoke(action.index)
        Log.i(TAG, "$name's action = $action")
        return when {
            CURR_BOARD_STATE.getWinner() == myChess -> getGameResultByWinner(myChess)
            CURR_BOARD_STATE.isFull() -> AIEnv.GameResult.RESULT_DRAW
            else -> AIEnv.GameResult.IN_PROGRESS
        }
    }

    private fun learnForSarsa(action: Action?) {
        lastStateForSarsa?.apply {
            engine.doLearning(qTable, currState, currAction, nextState, reward, action)
        }
    }

    override fun reset() {
        super.reset()
        lastStateForSarsa = null
    }

}

class SarsaLambdaEnvironmentImpl(name: String, myChess: ChessPieceState, opponentChess: ChessPieceState, qTable: QTable, updateListener: ((index: Int) -> Unit)?)
    : SarsaEnvironmentImpl(name, myChess, opponentChess, qTable, updateListener) {

    override val engine: IRLEngine
        get() = SarsaLambdaTicTacToeEngine()

    override fun reset() {
        super.reset()
        (engine as SarsaLambdaTicTacToeEngine).reset()
    }

}