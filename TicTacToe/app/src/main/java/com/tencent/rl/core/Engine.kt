package com.tencent.rl.core

import android.util.Log
import kotlin.random.Random

interface IRLEngine {

    fun getEpsilon(): Float

    fun getFeedBack(state: BoardState, action: Action): Pair<BoardState, Float>

    fun chooseAction(table: QTable, currState: BoardState): Action

    fun doLearning(table: QTable, currState: BoardState, action: Action, nextState: BoardState, reward: Float, nextAction: Action? = null)

}

abstract class BaseTicTacToeEngine: IRLEngine {

    override fun getFeedBack(state: BoardState, action: Action): Pair<BoardState, Float> {
        var reward = 0f
        val nextState = state.mutate(action)
        if (WinDetector.hasWon(nextState!!, Common.AI)) {
            reward = 1f
        } else if (WinDetector.oneMoreStepToWin(nextState!!, Common.HUMAN)) {
            reward = -1f
        }
        Log.d(Common.TAG, "AI ===> action=$action, reward=$reward")
        return Pair(nextState!!, reward)
    }

    override fun chooseAction(table: QTable, currState: BoardState): Action {
        var index = if (Random.nextFloat() > getEpsilon()) {
            currState.availableActionIndexes().random()
        } else {
            table.queryMax(currState)
        }
        return Action(index, Common.AI)
    }

}

class QLearningTicTacToeEngine: BaseTicTacToeEngine() {

    override fun getEpsilon() = Common.EPSILON

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

    override fun getEpsilon() = Common.EPSILON

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