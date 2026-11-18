package com.wearify.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wearify.app.R


internal data class ChatMessage(val text: String, val fromUser: Boolean)

private val StarterQuestions = listOf(
    "What signals can this wearable record?",
    "Where can I see my live readings?",
    "How do I connect a sensor?",
)

@Composable
internal fun ChatScreen(
    messages: List<ChatMessage>,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var question by remember { mutableStateOf("") }
    val scroll = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) scroll.animateScrollToItem(messages.lastIndex)
    }
    Column(modifier = modifier.fillMaxSize().background(WearifyBackground)) {
        AskHeader()
        LazyColumn(
            state = scroll,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (messages.isEmpty()) item { AskEmptyState(onSend = onSend) }
            items(messages) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(.84f),
                        color = if (message.fromUser) WearifyPine else WearifySurface,
                        shape = RoundedCornerShape(22.dp, 22.dp, if (message.fromUser) 6.dp else 22.dp, if (message.fromUser) 22.dp else 6.dp),
                    ) {
                        Text(message.text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge,
                            color = if (message.fromUser) Color.White else WearifyInk)
                    }
                }
            }
        }
        AskComposer(question = question, onQuestionChange = { question = it }, onSend = {
            if (question.isNotBlank()) {
                onSend(question.trim())
                question = ""
            }
        })
    }
}

@Composable
private fun AskHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Ask Clothing AI", style = MaterialTheme.typography.headlineMedium, color = WearifyInk)
            Text("Local chat preview", style = MaterialTheme.typography.bodySmall, color = WearifyMuted)
        }
        Box(Modifier.size(10.dp).background(WearifyMuted.copy(alpha = .55f), CircleShape))
    }
}

@Composable
private fun AskEmptyState(
    onSend: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent,
            shape = RoundedCornerShape(26.dp),
        ) {
            Row(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF124A9C),
                                Color(0xFF2468C8),
                                Color(0xFF4B83D8),
                            ),
                        ),
                    )
                    .padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ask about your wearable", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        "Try a question below or write your own. Messages stay on this screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = .74f),
                    )
                }
                Image(
                    painter = painterResource(R.drawable.home_guide_man_detailed_v3),
                    modifier = Modifier.size(112.dp),
                    contentDescription = "Clothing AI evidence guide",
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Text("Try asking", style = MaterialTheme.typography.labelLarge, color = WearifyMuted)
        StarterQuestions.forEach { prompt ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = "Ask Clothing AI",
                    ) { onSend(prompt) },
                color = WearifySurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, WearifyLine),
            ) {
                Text(prompt, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyLarge, color = WearifyInk)
            }
        }
    }
}

@Composable
private fun AskComposer(
    question: String,
    onQuestionChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val bottomPadding = if (isKeyboardVisible) 12.dp else 94.dp

    Surface(modifier = Modifier.fillMaxWidth(), color = WearifyBackground) {
        Column {
            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = bottomPadding)
                    .height(60.dp),
                placeholder = {
                    Text("Ask about your wearable")
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = WearifySurface,
                    unfocusedContainerColor = WearifySurface,
                    focusedBorderColor = WearifyPine,
                    unfocusedBorderColor = WearifyLine,
                ),
                trailingIcon = {
                    Surface(color = if (question.isNotBlank()) WearifyPine else WearifyLine, shape = CircleShape, modifier = Modifier.size(40.dp)) {
                        IconButton(onClick = onSend, enabled = question.isNotBlank()) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Ask Clothing AI", tint = Color.White)
                        }
                    }
                },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ChatPreview() {
    WearifyTheme { ChatScreen(messages = emptyList(), onSend = {}) }
}
