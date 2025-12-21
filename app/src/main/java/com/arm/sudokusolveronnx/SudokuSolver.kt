package com.arm.sudokusolveronnx

object SudokuSolver {
    fun solve(board: Array<IntArray>): Boolean {
        val pos = findEmpty(board) ?: return true
        val r = pos.first
        val c = pos.second
        for (v in 1..9) {
            if (isValid(board, r, c, v)) {
                board[r][c] = v
                if (solve(board)) return true
                board[r][c] = 0
            }
        }
        return false
    }

    private fun findEmpty(board: Array<IntArray>): Pair<Int, Int>? {
        for (r in 0 until 9) for (c in 0 until 9) if (board[r][c] == 0) return r to c
        return null
    }

    private fun isValid(board: Array<IntArray>, r: Int, c: Int, v: Int): Boolean {
        for (j in 0 until 9) if (board[r][j] == v) return false
        for (i in 0 until 9) if (board[i][c] == v) return false
        val br = (r / 3) * 3
        val bc = (c / 3) * 3
        for (i in br until br + 3) for (j in bc until bc + 3) if (board[i][j] == v) return false
        return true
    }
}