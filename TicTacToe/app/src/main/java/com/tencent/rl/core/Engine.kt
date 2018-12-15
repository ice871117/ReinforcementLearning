package com.tencent.rl.core

import android.util.Log
import kotlin.random.Random

interface IRLEngine {

    fun step(state: BoardState, action: Action): Pair<BoardState, Float>

    fun chooseAction(table: QTable, currState: BoardState, chess: ChessPieceState): Action

    fun doLearning(table: QTable, currState: BoardState, action: Action, nextState: BoardState, reward: Float, nextAction: Action? = null)

}

abstract class BaseTicTacToeEngine: IRLEngine {

    override fun step(state: BoardState, action: Action): Pair<BoardState, Float> {
        var reward = 0f
        val nextState = state.mutate(action)
        if (WinDetector.hasWon(nextState!!, action.chessPieceState)) {
            reward = 1f
        } else if (WinDetector.oneMoreStepToWin(nextState!!, Common.getOpponentChess(action.chessPieceState))) {
            reward = -1f
        }
        Log.d(Common.TAG, "AI ===> action=$action, reward=$reward")
        return Pair(nextState!!, reward)
    }

    override fun chooseAction(table: QTable, currState: BoardState, chess: ChessPieceState): Action {
        var index = if (Random.nextFloat() > Common.EPSILON) {
            currState.availableActionIndexes().random()
        } else {
            table.queryMax(currState)
        }
        return Action(index, chess)
    }

}

class QLearningTicTacToeEngine: BaseTicTacToeEngine() {

    /**
     * nextAction is never used by QLearning
     */
    override fun doLearning(table: QTable, currState: BoardState, action: Action, nextState: BoardState, reward: Float, nextAction: Action?) {
        val qPredict = table.getTable(currState)[action.index]
        val qTarget = if (nextState.isFull() || nextState.getWinner() != null) {
            reward
        } else {
            reward + Common.GAMMA * table.getTable(nextState).max()!!
        }
        table.getTable(currState)[action.index] = Common.ALPHA * (qTarget - qPredict)
    }

}

class SarsaTicTacToeEngine: BaseTicTacToeEngine() {

    /**
     * nextAction will be taken into account if provided in Sarsa
     */
    override fun doLearning(table: QTable, currState: BoardState, action: Action, nextState: BoardState, reward: Float, nextAction: Action?) {
        val qPredict = table.getTable(currState)[action.index]
        val qTarget = if (nextState.isFull() || nextState.getWinner() != null) {
            reward
        } else if (nextAction != null) {
            reward + Common.GAMMA * table.getTable(nextState)[nextAction.index]
        } else {
            // nextAction is null while game has not been finished yet, do nothing
            return
        }
        table.getTable(currState)[action.index] = Common.ALPHA * (qTarget - qPredict)
    }

    data class LastLerningState(val currState: BoardState, val currAction: Action, val nextState: BoardState, val reward: Float)

}

class SarsaLambdaTicTacToeEngine: BaseTicTacToeEngine() {

    private val eligibilityTable: QTable = QTable()

    /**
     * nextAction will be taken into account if provided in SarsaLambda
     */
    override fun doLearning(table: QTable, currState: BoardState, action: Action, nextState: BoardState, reward: Float, nextAction: Action?) {
        val qPredict = table.getTable(currState)[action.index]
        val qTarget = if (nextState.isFull() || nextState.getWinner() != null) {
            reward
        } else if (nextAction != null) {
            reward + Common.GAMMA * table.getTable(nextState)[nextAction.index]
        } else {
            // nextAction is null while game has not been finished yet, do nothing
            return
        }

        // update eligibility trace, to track the states which have already been met with all the way until some real reward occur
        eligibilityTable.reset(currState)
        eligibilityTable.getTable(currState)[action.index] = 1f

        // update reward to Q-table
        table.traverse {
            state, array ->
            array.addToEach(eligibilityTable.getTable(state), Common.ALPHA * (qTarget - qPredict))
        }

        // decay the eligibility table by given lambda
        eligibilityTable.traverse {
            _, array ->
            array.multiplyEachBy(Common.GAMMA * Common.LAMBDA)
        }
    }

    /**
     * Must be called every time a new episode is started
     */
    fun reset() {
        eligibilityTable.reset()
    }
}