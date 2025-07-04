package com.example.gomoku

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GameViewModel : ViewModel() {

    private val deepseekService = DeepseekService()
    private val _uiState = MutableStateFlow(GameState())
    val uiState: StateFlow<GameState> = _uiState.asStateFlow()

    fun handleIntent(intent: GameIntent) {
        Log.d("GameViewModel", "Handling intent: $intent")
        when (intent) {
            is GameIntent.PlacePiece -> placePiece(intent.x, intent.y)
            GameIntent.ResetGame -> resetGame()
            GameIntent.AiMove -> makeAiMove()
        }
    }

    private fun placePiece(x: Int, y: Int) {
        Log.d("GameViewModel", "placePiece called for ($x, $y)")
        if (_uiState.value.winner != null || _uiState.value.isDraw) {
            Log.d("GameViewModel", "Game over or draw. Not placing piece.")
            return // Game is already over or draw
        }

        val currentPieces = _uiState.value.chessPieces
        if (currentPieces.any { piece: ChessPiece -> piece.x == x && piece.y == y }) {
            Log.d("GameViewModel", "Cell ($x, $y) is already occupied. Not placing piece.")
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
        Log.d("GameViewModel", "Piece placed at ($x, $y). Current player: ${_uiState.value.currentPlayer}")

        if (winner == null && !isDraw && _uiState.value.currentPlayer == Color.White) { // Assuming AI is White
            Log.d("GameViewModel", "It's AI's turn. Triggering AI move.")
            makeAiMove()
        }
    }

    private fun makeAiMove() {
        Log.d("GameViewModel", "makeAiMove called. Setting isAiThinking to true.")
        _uiState.value = _uiState.value.copy(isAiThinking = true)
        viewModelScope.launch {
            val prompt = buildAiPrompt(_uiState.value.chessPieces)
            Log.d("GameViewModel", "AI Prompt: \n$prompt")
            val response = withContext(Dispatchers.IO) {
                Log.d("GameViewModel", "Calling DeepseekService.generateCompletion...")
                deepseekService.generateCompletion(prompt, "deepseek-chat") // Use the specified model
            }
            Log.d("GameViewModel", "DeepseekService.generateCompletion returned: $response")

            response?.let {
                val (x, y) = parseAiResponse(it)
                Log.d("GameViewModel", "Parsed AI response: x=$x, y=$y")
                _uiState.value = _uiState.value.copy(isAiThinking = false) // Set to false before placing piece
                if (x != null && y != null) {
                    placePiece(x, y)
                } else {
                    Log.e("GameViewModel", "Failed to parse AI response: $it")
                }
            } ?: run { 
                Log.e("GameViewModel", "DeepseekService.generateCompletion returned null response.") 
                _uiState.value = _uiState.value.copy(isAiThinking = false) // Also set to false if response is null
            }
        }
    }

    private fun buildAiPrompt(pieces: List<ChessPiece>): String {
        val board = Array(BOARD_SIZE) { Array(BOARD_SIZE) { "-" } }
        pieces.forEach { piece ->
            board[piece.y][piece.x] = if (piece.color == Color.Black) "B" else "W"
        }

        val boardString = board.joinToString("\n") { row -> row.joinToString(" ") }

        return """
            You are an expert Gomoku AI player. Your goal is to win the game.
            The board size is ${BOARD_SIZE}x${BOARD_SIZE}. You need to get 5 in a row to win.
            Current player is ${if (_uiState.value.currentPlayer == Color.Black) "B" else "W"}.
            The current board state is:
            $boardString

            Your task is to choose the best possible move. Pay close attention to the opponent's moves.
            If the opponent has three pieces in a row, you MUST block one end of that line.
            If the opponent has four pieces in a row, you MUST block both ends of that line to prevent them from winning.

            IMPORTANT: You MUST choose a move to an EMPTY cell. Do NOT choose a cell that is already occupied.

            All coordinates are 0-indexed, meaning they range from 0 to ${BOARD_SIZE - 1} for both row and column.

            Think step-by-step and generate 3 best moves. List them in order of preference.
            Respond only with the row and column of your chosen moves, each on a new line, in the format:
            row1,col1
            row2,col2
            row3,col3
            For example:
            7,8
            6,7
            8,9
            """.trimIndent()
    }

    private fun parseAiResponse(response: String): Pair<Int?, Int?> {
        val lines = response.trim().split("\n")
        for (line in lines) {
            val parts = line.trim().split(",")
            if (parts.size == 2) {
                val x = parts[0].toIntOrNull()
                val y = parts[1].toIntOrNull()
                if (x != null && y != null) {
                    return Pair(x, y)
                }
            }
        }
        return Pair(null, null)
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
    val isDraw: Boolean = false,
    val isAiThinking: Boolean = false
)

data class ChessPiece(val x: Int, val y: Int, val color: Color)

sealed class GameIntent {
    data class PlacePiece(val x: Int, val y: Int) : GameIntent()
    object ResetGame : GameIntent()
    object AiMove : GameIntent()
}