package com.rs.s2s

import android.text.style.BackgroundColorSpan
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * description
 * @author whs
 * @date 2024/9/1
 */
@Composable
fun ChatScreen(
    isRecording:Boolean,
    messages: MutableList<ChatMessage>,
    serverAddress: String,
    apiKey: String,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSave: (url: String, key: String) -> Unit = { _, _,-> },
    ) {
    var showDialog by remember { mutableStateOf(false) }
    var serverAddress by remember { mutableStateOf(serverAddress) }
    var apiKey by remember { mutableStateOf(apiKey) }


    Column(modifier = Modifier
        .fillMaxSize(),  verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally) {
        MessageList(messages = messages, modifier = Modifier.weight(1f).fillMaxWidth())
        IconButton(
            onClick = {
                showDialog = true // 点击图标显示对话框
            },
            modifier = Modifier
                .align(Alignment.End)
                .padding(16.dp)

        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_settings), // 使用设置图标
                tint = Color.Unspecified,
                contentDescription = "Settings",
                modifier = Modifier.size(48.dp)
            )

        }
        UserInput(isRecording, onStartRecording, onStopRecording)
        if (showDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDialog = false // 点击对话框外部或取消按钮时关闭对话框
                },
                title = {
                    Text(text = "设置服务器地址")
                },
                text = {
                    Column {
                        OutlinedTextField(
                            value = serverAddress,
                            onValueChange = { serverAddress = it },
                            label = { Text("Enter Server IP Address") },
//                                placeholder = { Text(serverAddress) }, 限制为单行
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Uri) // 设置键盘类型为数字)
                        )
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Enter Server apiKey") },
//                                placeholder = { Text(serverAddress) }, 限制为单行
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Unspecified) // 设置键盘类型为数字)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDialog = false // 点击确定后关闭对话框
                            onSave(serverAddress, apiKey)
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDialog = false // 点击取消后关闭对话框
                        }
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

@Composable
fun MessageList(messages: MutableList<ChatMessage>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.padding(8.dp)) {
        items(messages) { message ->
            MessageItem(message)
        }
    }
}

@Composable
fun MessageItem(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.TopEnd else Alignment.TopStart
    val backgroundColor = if (message.isUser) Color.Green else Color.White
    val textColor = Color.Black

//    val backgroundColor = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
//    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .clip(RoundedCornerShape(16.dp))  // 设置圆角半径
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
                Text(text = message.text, color = textColor,fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun UserInput(
//    onSendMessage: (String, Boolean) -> Unit,
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    var text by remember { mutableStateOf("") }
//    var isRecording by remember { mutableStateOf(false) }
    val buttonText = when {
        isRecording -> "录音中"
        else -> "按住说话"
    }

    val buttonColor = when {
        isRecording -> listOf(
            Color(0xFFB71C1C), // 自定义深红色
            Color(0xFFFF5252)  // 自定义浅红色
        ) // 已连接且按
        else -> listOf(
            Color(0xFF2E7D32), // 自定义深绿色
            Color(0xFF66BB6A)  // 自定义浅绿色
        ) // 已连接且抬起状态的渐变色
    }

    Box(contentAlignment = Alignment.Center,
        modifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onStartRecording()
                        val released = tryAwaitRelease()
                        // 如果用户抬起按钮，停止录音
                        if (released) {
                            onStopRecording()
                        }
                    },
                )
            }
            .size(120.dp)
            .padding(16.dp)
    ) {
        Canvas(
            modifier = Modifier
                .size(90.dp)
        ) {
            drawCircle(
                brush = Brush.linearGradient(buttonColor), // 使用渐变效果填充颜色
                radius = size.minDimension / 2
            )
            // 边框效果，用于增强3D感
            drawCircle(
                color = Color.Black.copy(alpha = 0.2f),
                radius = size.minDimension / 2,
                style = Stroke(width = 6f)
            )
        }

        Text(
            text = buttonText,
            color = Color.White,
            fontSize = 16.sp
        )
    }

}