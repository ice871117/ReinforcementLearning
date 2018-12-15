package com.tencent.rl.core

import android.util.Log


object WinDetector {

    val VERTICAL_STRATEGY = OrthogonalStrategy(0)
    val HORIZONTAL_STRATEGY = OrthogonalStrategy(1)
    val DIAGONAL1 = DiagonalStrategy(0)
    val DIAGONAL2 = DiagonalStrategy(1)

    enum class Direction(val strategy: DetectStrategy) {
        HORIZONTAL(HORIZONTAL_STRATEGY),
        VERTICAL(VERTICAL_STRATEGY),
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