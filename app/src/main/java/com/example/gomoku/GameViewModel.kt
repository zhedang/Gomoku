package com.example.gomoku

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GameViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GameState())
    val uiState: StateFlow<GameState> = _uiState.asStateFlow()

    fun handleIntent(intent: GameIntent) {
        when (intent) {
            is GameIntent.PlacePiece -> placePiece(intent.x, intent.y)
            GameIntent.ResetGame -> resetGame()
        }
    }

    private fun placePiece(x: Int, y: Int) {
        if (_uiState.value.winner != null || _uiState.value.isDraw) {
            return // Game is already over
        }

        val currentPieces = _uiState.value.chessPieces
        if (currentPieces.any { piece: ChessPiece -> piece.x == x && piece.y == y }) {
            return // Cell is already occupied
        }

        val newPiece = ChessPiece(x, y, _uiState.value.currentPlayer)
        val newPieces = currentPieces + newPiece

        val winner = checkForWinner(newPieces, newPiece)
        val isDraw = newPieces.size == BOARD_SIZE * BOARD_SIZE && winner == null

        _uiState.value = _uiState.value.copy(
            chessPieces = newPieces,
            currentPlayer = if (_uiState.value.currentPlayer == Color.Black) Color.White else Color.Black,
            winner = winner,
            isDraw = isDraw
        )
    }

    private fun checkForWinner(pieces: List<ChessPiece>, lastPiece: ChessPiece): Color? {
        val directions = listOf(
            Pair(1, 0),   // Horizontal
            Pair(0, 1),   // Vertical
            Pair(1, 1),   // Diagonal /
            Pair(1, -1)   // Diagonal \
        )

        for (direction in directions) {
            val count = 1 + countStones(pieces, lastPiece, direction.first, direction.second) +
                    countStones(pieces, lastPiece, -direction.first, -direction.second)
            if (count >= 5) {
                return lastPiece.color
            }
        }
        return null
    }

    private fun countStones(pieces: List<ChessPiece>, startPiece: ChessPiece, dx: Int, dy: Int): Int {
        var count = 0
        var currentX = startPiece.x + dx
        var currentY = startPiece.y + dy

        while (currentX in 0 until BOARD_SIZE && currentY in 0 until BOARD_SIZE) {
            val nextPiece = pieces.find { it.x == currentX && it.y == currentY && it.color == startPiece.color }
            if (nextPiece != null) {
                count++
                currentX += dx
                currentY += dy
            } else {
                break
            }
        }
        return count
    }

    private fun resetGame() {
        _uiState.value = GameState()
    }

    companion object {
        const val BOARD_SIZE = 15
    }
}

data class GameState(
    val chessPieces: List<ChessPiece> = emptyList(),
    val currentPlayer: Color = Color.Black,
    val winner: Color? = null,
    val isDraw: Boolean = false
)

data class ChessPiece(val x: Int, val y: Int, val color: Color)

sealed class GameIntent {
    data class PlacePiece(val x: Int, val y: Int) : GameIntent()
    object ResetGame : GameIntent()
}