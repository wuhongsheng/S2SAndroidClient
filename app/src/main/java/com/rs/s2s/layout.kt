package com.rs.s2s

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * description
 * @author whs
 * @date 2024/9/1
 */
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    onSendMessage: (String, Boolean) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MessageList(messages = messages, modifier = Modifier.weight(1f))
        UserInput(onSendMessage, onStartRecording, onStopRecording)
    }
}

@Composable
fun MessageList(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.padding(8.dp)) {
        items(messages) { message ->
            MessageItem(message)
        }
    }
}

@Composable
fun MessageItem(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val backgroundColor = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .background(backgroundColor)
                .padding(8.dp)
        ) {
            if (message.isVoice) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.clickable {
                        // 播放音频逻辑
                    }
                )
            } else {
                Text(text = message.text, color = textColor)
            }
        }
    }
}

@Composable
fun UserInput(
    onSendMessage: (String, Boolean) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = {
            if (!isRecording) {
                onStartRecording()
                isRecording = true
            } else {
                onStopRecording()
                onSendMessage(text, true)
                text = ""
                isRecording = false
            }
        }) {
            Text(if (isRecording) "⏹️ Stop & Send" else "🎤 Start Recording")
        }

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(text = "Type a message...") }
        )

        Spacer(modifier = Modifier.width(8.dp))

        Button(onClick = {
            onSendMessage(text, false)
            text = ""
        }) {
            Text("Send")
        }
    }
}