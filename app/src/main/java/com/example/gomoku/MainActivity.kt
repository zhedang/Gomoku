package com.example.gomoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gomoku.ui.theme.GomokuTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GomokuTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val gameState by gameViewModel.uiState.collectAsState()
                    GameScreen(
                        gameState = gameState,
                        onIntent = { gameViewModel.handleIntent(it) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun GameScreen(
    gameState: GameState,
    onIntent: (GameIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = getStatusText(gameState),
            fontSize = 24.sp,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Chessboard(
            chessPieces = gameState.chessPieces,
            onTap = { x, y -> onIntent(GameIntent.PlacePiece(x, y)) },
            isClickable = !gameState.isAiThinking && gameState.winner == null && !gameState.isDraw
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onIntent(GameIntent.ResetGame) }, enabled = !gameState.isAiThinking) {
            Text(text = "Reset Game")
        }
    }
}

@Composable
private fun getStatusText(gameState: GameState): String {
    return when {
        gameState.isAiThinking -> "AI is thinking..."
        gameState.winner != null -> {
            val winnerName = if (gameState.winner == Color.Black) "Black" else "White"
            "$winnerName wins!"
        }
        gameState.isDraw -> "It's a draw!"
        else -> {
            val currentPlayerName = if (gameState.currentPlayer == Color.Black) "Black" else "White"
            "$currentPlayerName's turn"
        }
    }
}

@Composable
fun Chessboard(
    chessPieces: List<ChessPiece>,
    onTap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    isClickable: Boolean = true
) {
    val boardSize = GameViewModel.BOARD_SIZE
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(if (isClickable) Modifier.pointerInput(Unit) {
                detectTapGestures { offset ->
                    val gridSize = size.width / (boardSize - 1)
                    val x = (offset.x / gridSize).roundToInt()
                    val y = (offset.y / gridSize).roundToInt()

                    if (x in 0 until boardSize && y in 0 until boardSize) {
                        onTap(x, y)
                    }
                }
            } else Modifier)
    ) {
        val gridSize = size.width / (boardSize - 1)

        // Draw grid lines
        for (i in 0 until boardSize) {
            val start = i * gridSize
            drawLine(Color.Black, start = Offset(start, 0f), end = Offset(start, size.height))
            drawLine(Color.Black, start = Offset(0f, start), end = Offset(size.width, start))
        }

        // Draw chess pieces
        chessPieces.forEach { piece ->
            val center = Offset(piece.x * gridSize, piece.y * gridSize)
            drawCircle(
                color = piece.color,
                radius = gridSize / 2.2f,
                center = center
            )
            // Add a border to white pieces for better visibility
            if (piece.color == Color.White) {
                drawCircle(
                    color = Color.Black,
                    radius = gridSize / 2.2f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GameScreenPreview() {
    GomokuTheme {
        val gameState = GameState(
            chessPieces = listOf(
                ChessPiece(7, 7, Color.Black),
                ChessPiece(7, 8, Color.White)
            ),
            currentPlayer = Color.Black
        )
        GameScreen(gameState = gameState, onIntent = {})
    }
}