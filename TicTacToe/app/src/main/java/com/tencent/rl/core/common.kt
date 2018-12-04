package com.tencent.rl.core

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.Closeable
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.Exception
import java.lang.IllegalStateException
import java.util.*

enum class ChessPieceState(val value: Int) {
    NONE(-1),
    CIRCLE(0),
    CROSS(1)
}

object Common {
    const val SIZE = 3
    const val BOARD_TOTAL_SIZE = SIZE * SIZE
    var EPSILON = 0.9f                   // 贪婪度 greedy
    const val ALPHA = 0.1f                     // 学习率
    const val GAMMA = 0.9f                     // 奖励递减值
    const val LAMBDA = 0.7f
    val HUMAN = ChessPieceState.CROSS
    val AI = ChessPieceState.CIRCLE
    const val TAG = "RL-TicTacToe"
    var SAVE_PATH: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun actionIndex2Coord(index: Int): Pair<Int, Int> {
        val x = index / SIZE
        val y = index % SIZE
        return Pair(x, y)
    }

    fun coord2actionIndex(x: Int, y: Int): Int {
        return x * SIZE + y
    }

    fun chessPieceState2String(chessPieceState: ChessPieceState): String {
        return when (chessPieceState) {
            ChessPieceState.NONE -> "None"
            ChessPieceState.CIRCLE -> "O"
            ChessPieceState.CROSS -> "X"
        }
    }

    fun closeSilently(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun runOnUIThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block.invoke()
        } else {
            mainHandler.post(block)
        }
    }
}

data class Action(val index: Int, val chessPieceState: ChessPieceState) {
    override fun toString(): String {
        val pair = Common.actionIndex2Coord(this.index)
        return "Set [${pair.first}, ${pair.second}] to ${Common.chessPieceState2String(chessPieceState)}"
    }
}


/**
 * record the state of a square chessboard
 */
class BoardState(val size: Int) : Cloneable, Serializable {

    companion object {
        @JvmStatic
        private val serialVersionUID = 1L
    }

    private val matrix = Array(size) { Array(size) { ChessPieceState.NONE } }
    @Transient
    private var winner: ChessPieceState? = null

    fun get(x: Int, y: Int): ChessPieceState {
        return matrix[x][y]
    }

    fun mutate(action: Action): BoardState? {
        val coord = Common.actionIndex2Coord(action.index)
        if (this.matrix[coord.first][coord.second] != ChessPieceState.NONE) {
            return null
        }
        val ret = clone()
        ret.matrix[coord.first][coord.second] = action.chessPieceState
        return ret
    }

    fun getWinner(): ChessPieceState? {
        if (winner == null) {
            for (state in listOf(ChessPieceState.CIRCLE, ChessPieceState.CROSS)) {
                if (WinDetector.hasWon(this, state)) {
                    winner = state
                    break
                }
            }
        }
        return winner
    }

    fun isFull(): Boolean {
        for (i in 0 until size) {
            for (j in 0 until size) {
                if (matrix[i][j] == ChessPieceState.NONE) {
                    return false
                }
            }
        }
        return true
    }

    fun availableActionIndexes(): List<Int> {
        val result = mutableListOf<Int>()
        var index = 0
        for (i in 0 until size) {
            for (j in 0 until size) {
                if (matrix[i][j] == ChessPieceState.NONE) {
                    result.add(index)
                }
                index++
            }
        }
        return result
    }

    override fun hashCode(): Int {
        var result = 1
        for (array in matrix) {
            result = 31 * result + Arrays.hashCode(array)
        }
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other !is BoardState) {
            // fast fail
            return false
        }
        for ((index, array) in matrix.withIndex()) {
            if (!Arrays.equals(array, other.matrix[index])) {
                return false
            }
        }
        return true
    }

    public override fun clone(): BoardState {
        val ret = BoardState(size)
        for ((index, array) in matrix.withIndex()) {
            ret.matrix[index] = Arrays.copyOf(array, array.size)
        }
        return ret
    }

    override fun toString(): String {
        val buffer = StringBuilder()
        buffer.append("BoardState:{")
        for (i in 0 until size) {
            buffer.append("[")
            for (j in 0 until size) {
                buffer.append(matrix[i][j])
                if (j < size - 1) {
                    buffer.append(",")
                }
            }
            buffer.append("]")
            if (i < size - 1) {
                buffer.append(",")
            }
        }
        buffer.append("}")
        return super.toString()
    }
}

inline fun FloatArray.reset() {
    for (i in 0 until this.size) {
        this[i] = 0f
    }
}

inline fun FloatArray.addToEach(floatArray: FloatArray, multiplier: Float) {
    if (this.size != floatArray.size) {
        throw IllegalStateException("addToEach() failed!! Size must be equal, caller.size=${this.size} callee.size=${floatArray.size}")
    }
    for (i in 0 until this.size) {
        this[i] += floatArray[i] * multiplier
    }
}

inline fun FloatArray.multiplyEachBy(multiplier: Float) {
    for (i in 0 until this.size) {
        this[i] *= multiplier
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

    fun traverse(block: (state: BoardState, array: FloatArray) -> Unit) {
        for ( (key, value) in table) {
            block.invoke(key, value)
        }
    }

    fun reset(state: BoardState? = null) {
        if (state == null) {
            for ( (_, value) in table) {
                value.reset()
            }
        } else {
            val tableForState = table[state]
            if (tableForState == null) {
                table[state] = buildActionArray()
            } else {
                tableForState.reset()
            }
        }
    }

    fun selfCheck() {
        for (key in table.keys) {
            for (innerKey in table.keys) {
                if (key !== innerKey && key.equals(innerKey)) {
                    Log.w(Common.TAG, "error found left(${key.hashCode()}): $key,\n right(${innerKey.hashCode()}): $innerKey")
                }
            }
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