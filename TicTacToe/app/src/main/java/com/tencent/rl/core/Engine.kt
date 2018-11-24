package com.tencent.rl.core

import android.util.Log
import kotlin.random.Random

interface IRLEngine {

    fun getEpsilon(): Float

    fun getFeedBack(state: BoardState, action: Action): Pair<BoardState, Float>

    fun chooseAction(table: QTable, currState: BoardState): Action

    fun doLearning(table: QTable, currState: BoardState, action: Action, nextState: BoardState, reward: Float)

}

abstract class BaseTicTacToeEngine: IRLEngine {

    override fun getFeedBack(state: BoardState, action: Action): Pair<BoardState, Float> {
        var reward = 0f
        val nextState = state.mutate(action)
        if (WinDetector.hasWon(nextState!!, Common.AI)) {
            reward = 1f
        } else if (WinDetector.oneMoreStepToWin(nextState!!, Common.HUMAN)) {
            reward = -0.3f
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

    override fun doLearning(table: QTable, currState: BoardState, action: Action, nextState: BoardState, reward: Float) {
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

    override fun doLearning(table: QTable, currState: BoardState, action: Action, nextState: BoardState, reward: Float) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}