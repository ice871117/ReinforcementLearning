package com.tencent.rl.core

import android.os.Handler
import android.os.Looper


interface SimulatePlayer : Runnable {

    fun play(state: BoardState): Int

    fun start()

    fun stop()

    fun isStarted(): Boolean

    fun reset()
}

/**
 * The trainer for AI
 * @param myChess the chess which is assigned to this player
 * @param opponentEnv opponent AI env
 * @param interval the time interval before each action of this player
 * @param doWhenPlay perform the trainer's action.
 */
abstract class AbsPlayer(protected val myChess: ChessPieceState, protected val opponentEnv: AIEnv, private val interval: Long, private val doWhenPlay: (Int, AbsPlayer) -> Unit) : SimulatePlayer {

    private val handler = Handler(Looper.getMainLooper())
    private var started = false

    override fun run() {
        doWhenPlay(play(opponentEnv.getCurrentState()), this)
        if (started) {
            handler.postDelayed(this, interval)
        }
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

    override fun reset() {
    }

}

/**
 * An idiot player who wins by luck.
 */
class RandomPlayer(chess: ChessPieceState, opponentEnv: AIEnv, interval: Long, doWhenPlay: (Int, AbsPlayer) -> Unit) : AbsPlayer(chess, opponentEnv, interval, doWhenPlay) {
    override fun play(state: BoardState): Int {
        return WinDetector.findBestStep(opponentEnv.getCurrentState(), myChess) ?: state.availableActionIndexes().random()
    }
}

/**
 * Use RL AI play with itself.
 */
class AIPlayer (chess: ChessPieceState, opponentEnv: AIEnv, interval: Long, doWhenPlay: (Int, AbsPlayer) -> Unit) : AbsPlayer(chess, opponentEnv, interval, doWhenPlay) {

    private var nextActionIndex = -1
    private val aiPlayer = AIEnv(Common.AI_PLAYER_2, chess) {
        index ->
        nextActionIndex = index
    }

    init {
        aiPlayer.loadQTable(Common.SAVE_DIR_PATH)
        aiPlayer.setEngine(AIEnv.EngineType.QLEARNING)
    }

    override fun play(state: BoardState): Int {
        aiPlayer.updateByMe()
        return nextActionIndex
    }

    override fun stop() {
        super.stop()
        aiPlayer.saveQTable(Common.SAVE_DIR_PATH)
    }

    override fun reset() {
        aiPlayer.reset()
    }

}

/**
 * A master player who knows taolu (set traps)
 */
class SophisticatedPlayer(chess: ChessPieceState, opponentEnv: AIEnv, interval: Long, doWhenPlay: (Int, AbsPlayer) -> Unit) : AbsPlayer(chess, opponentEnv, interval, doWhenPlay) {

    private val chain = StrategyChain()

    init {
        chain.addStrategy(FinalAttack())
                .addStrategy(FinalDefense())
                .addStrategy(TwoSideDefense())
                .addStrategy(CenterCornerAttack())
                .addStrategy(TwoSideAttack())
                .addStrategy(RandomPlay())
    }

    override fun play(state: BoardState): Int {
        var ret = -1
        for (strategy in chain) {
            ret = strategy.perform(opponentEnv.getCurrentState(), myChess)
            if (ret != -1) {
                break
            }
        }
        assert(ret != -1)
        return ret
    }
}


class StrategyChain : Iterable<Strategy> {

    private val strategies = mutableListOf<Strategy>()

    fun addStrategy(strategy: Strategy): StrategyChain {
        strategies.add(strategy)
        return this
    }

    override fun iterator() = strategies.iterator()

}

interface Strategy {
    fun perform(state: BoardState, chess: ChessPieceState): Int
}

inline fun filterAvailableIndex(availableList: List<Int>, indexArr: List<Int>): Int {
    val availableSet = availableList.intersect(indexArr)
    return if (availableSet.isEmpty()) -1 else availableSet.first()
}

/**
 * Take the center pos and then try a two side attack, for example:
 *
 * 2 - -
 * 3 1 -
 * - - -
 *
 * or
 *
 * - - -
 * - 1 -
 * - 3 2
 *
 * - means None
 * if steps like those above are performed, you win the game.
 */
class CenterCornerAttack : Strategy {

    override fun perform(state: BoardState, chess: ChessPieceState): Int {
        var ret = -1
        val availableList = state.availableActionIndexes()
        if (state.isEmpty() || state.get(1, 1) == ChessPieceState.NONE) {
            ret = Common.coord2actionIndex(1, 1)
        } else if (state.get(1, 1) == chess) {
            ret = when (chess) {
                state.get(0, 0) -> filterAvailableIndex(availableList, listOf(Common.coord2actionIndex(1, 0), Common.coord2actionIndex(0, 1)))
                state.get(2, 0) -> filterAvailableIndex(availableList, listOf(Common.coord2actionIndex(1, 0), Common.coord2actionIndex(2, 1)))
                state.get(2, 2) -> filterAvailableIndex(availableList, listOf(Common.coord2actionIndex(1, 2), Common.coord2actionIndex(2, 1)))
                state.get(0, 2) -> filterAvailableIndex(availableList, listOf(Common.coord2actionIndex(0, 1), Common.coord2actionIndex(1, 2)))
                else -> -1
            }
        }
        if (ret == -1) {
            ret = if (state.get(0, 0) == ChessPieceState.NONE && state.get(2, 2) == ChessPieceState.NONE) {
                listOf(Common.coord2actionIndex(0, 0), Common.coord2actionIndex(2, 2)).random()
            } else if (state.get(2, 0) == ChessPieceState.NONE && state.get(0, 2) == ChessPieceState.NONE) {
                listOf(Common.coord2actionIndex(2, 0), Common.coord2actionIndex(0, 2)).random()
            } else {
                when {
                    state.get(0, 0) == ChessPieceState.NONE -> Common.coord2actionIndex(0, 0)
                    state.get(2, 0) == ChessPieceState.NONE -> Common.coord2actionIndex(2, 0)
                    state.get(2, 2) == ChessPieceState.NONE -> Common.coord2actionIndex(2, 2)
                    state.get(0, 2) == ChessPieceState.NONE -> Common.coord2actionIndex(0, 2)
                    else -> -1
                }
            }
        }
        if (!state.availableActionIndexes().contains(ret)) {
            ret = -1
        }
        return ret
    }

}

/**
 * Take the corner pos and then try a two side attack, for example:
 *
 * 1 - -
 * - - -
 * 3 - 2
 *
 * or
 *
 * 3 - 1
 * - - -
 * 2 - -
 *
 * - means None
 * if steps like those above are performed, you win the game.
 */
class TwoSideAttack : Strategy {

    override fun perform(state: BoardState, chess: ChessPieceState): Int {
        var ret = -1
        val availableList = state.availableActionIndexes()
        ret = when (chess) {
            state.get(0, 0) -> filterAvailableIndex(availableList, listOf(Common.coord2actionIndex(2, 2)))
            state.get(2, 0) -> filterAvailableIndex(availableList, listOf(Common.coord2actionIndex(0, 2)))
            state.get(2, 2) -> filterAvailableIndex(availableList, listOf(Common.coord2actionIndex(0, 0)))
            state.get(0, 2) -> filterAvailableIndex(availableList, listOf(Common.coord2actionIndex(2, 0)))
            else -> -1
        }
        if (ret == -1) {
            ret = when {
                state.get(0, 0) == ChessPieceState.NONE -> Common.coord2actionIndex(0, 0)
                state.get(2, 0) == ChessPieceState.NONE -> Common.coord2actionIndex(2, 0)
                state.get(2, 2) == ChessPieceState.NONE -> Common.coord2actionIndex(2, 2)
                state.get(0, 2) == ChessPieceState.NONE -> Common.coord2actionIndex(0, 2)
                else -> -1
            }
        }
        return ret
    }
}

/**
 * If there is one more step to win, choose it.
 */
class FinalAttack : Strategy {

    override fun perform(state: BoardState, chess: ChessPieceState): Int {
        return WinDetector.findBestStep(state, chess) ?: -1
    }
}

/**
 * Just choose an available position randomly.
 */
class RandomPlay : Strategy {

    override fun perform(state: BoardState, chess: ChessPieceState): Int {
        return state.availableActionIndexes().random()
    }
}

/**
 * This will be performed when the opponent get one more step to win.
 */
class FinalDefense : Strategy {

    override fun perform(state: BoardState, chess: ChessPieceState): Int {
        return WinDetector.findBestStep(state, Common.getOpponentChess(chess)) ?: -1
    }
}

/**
 * Defend from that the opponent player might perform a attack that will make him win on two sides, for example:
 *
 * X O -
 * - O -
 * X - X
 *
 * - means None
 * if it is the O's time to perform action, there is no way to stop the X from winning this game.
 */
class TwoSideDefense : Strategy {

    override fun perform(state: BoardState, chess: ChessPieceState): Int {
        var ret = -1
        val opponentChess = Common.getOpponentChess(chess)
        if (state.get(1, 1) == ChessPieceState.NONE) {
            ret = Common.coord2actionIndex(1, 1)
        } else {
            if (state.get(1, 1) == chess) {
                if ((state.get(0, 0) == opponentChess && state.get(2, 2) == opponentChess)
                    || (state.get(2, 0) == opponentChess && state.get(0, 2) == opponentChess)) {
                    ret = when {
                        state.get(0, 1) == ChessPieceState.NONE -> Common.coord2actionIndex(0, 1)
                        state.get(1, 0) == ChessPieceState.NONE -> Common.coord2actionIndex(1, 0)
                        state.get(1, 2) == ChessPieceState.NONE -> Common.coord2actionIndex(1, 2)
                        state.get(2, 1) == ChessPieceState.NONE -> Common.coord2actionIndex(2, 1)
                        else -> -1
                    }
                }
                if (ret == -1) {
                    val opInRow0 = state.containsInRow(0, opponentChess)
                    val opInRow2 = state.containsInRow(2, opponentChess)
                    val opInColumn0 = state.containsInColumn(0, opponentChess)
                    val opInColumn2 = state.containsInColumn(2, opponentChess)
                    ret = if (opInColumn0 && opInRow0) {
                        Common.coord2actionIndex(0, 0)
                    } else if (opInColumn0 && opInRow2) {
                        Common.coord2actionIndex(2, 0)
                    } else if (opInColumn2 && opInRow0) {
                        Common.coord2actionIndex(0, 2)
                    } else if (opInColumn2 && opInRow2) {
                        Common.coord2actionIndex(2, 2)
                    } else {
                        -1
                    }
                }
            }
        }
        if (!state.availableActionIndexes().contains(ret)) {
            ret = -1
        }
        return ret
    }
}