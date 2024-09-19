package com.rs.s2s


/**
 * description
 * @author whs
 * @date 2024/9/14
 */

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RecordingButton(
    isConnected: Boolean,
    isThinking: Boolean,
    onPress: () -> Unit,
    onReleased: () -> Unit,
) {
    // 定义按钮的状态，已连接时需要区分按下和抬起的状态
    var isPressed by remember { mutableStateOf(false) }

    // 按钮的颜色和样式变化根据状态
    val buttonColor = when {
//        !isConnected -> Color.Gray // 未连接状态
//        isPressed -> Color.Blue // 已连接且按下状态
//        else -> Color.Green // 已连接且抬起状态
        !isConnected -> listOf(Color.Gray, Color.LightGray) // 未连接状态的渐变色
        isPressed -> listOf(
            Color(0xFFB71C1C), // 自定义深红色
            Color(0xFFFF5252)  // 自定义浅红色
        ) // 已连接且按
        else -> listOf(
            Color(0xFF2E7D32), // 自定义深绿色
            Color(0xFF66BB6A)  // 自定义浅绿色
        ) // 已连接且抬起状态的渐变色
    }

    // 根据状态设置匹配的阴影颜色
//    val shadowColor = when {
//        !isConnected -> Color(0xFF888888) // 灰色阴影
//        isPressed -> Color(0xFF8B0000) // 深红色阴影
//        else -> Color(0xFF004D40) // 深绿色阴影
//    }


    val buttonText = when {
        !isConnected -> "未连接"
        isThinking -> "思考中"
        isPressed -> "聆听中"
        else -> "已连接"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(100.dp)
            .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onPress()
                            val released = tryAwaitRelease()
                            // 如果用户抬起按钮，停止录音
                            if (released) {
                                onReleased()
                            }
                        },
                    )

            }
//            .clickable(
//                onClick = {
//                    if (isConnected && !isThinking) { // 如果不是思考中状态时才允许点击
//                        isPressed = !isPressed
//                        onButtonClick(isPressed)
//                    }
//                }
//            )
    ) {
        if (isThinking) {
            // 在思考中状态下显示 CircularProgressIndicator
            CircularProgressIndicator(
                color = Color.Blue, // 你可以根据需求调整颜色
                strokeWidth = 4.dp,
                modifier = Modifier.size(50.dp)
            )
        } else {
            // 绘制带有渐变色的3D圆形按钮
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

            // 在按钮内部显示提示文字
            Text(
                text = buttonText,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
@Preview
fun RecordingButtonPreview() {
    var isConnected by remember { mutableStateOf(true) }
    var isThinking by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().background(Color.White) // 设置预览背景为白色

    ) {
        RecordingButton(isConnected = isConnected,isThinking=isThinking, onPress = {

            // 按下按钮后的操作

        }, onReleased = {

        }
        )

//        Box(
//            modifier = Modifier
//                .size(56.dp)
//                .clip(CircleShape)
//                .background(buttonColor)
//                .clickable(
//                    interactionSource = interactionState,
//                    indication = null,
//                    onClick = {
//                        checkAndRequestPermission()
//                        if (isRecording) {
//                            // 开始录音并处理语音识别逻辑
//                            speechRecognizer.startListening(intent)
//                        } else {
//                            // 停止录音
//                            speechRecognizer.stopListening()
//                        }
//                    }
//                ),
//            contentAlignment = Alignment.Center
//        ) {
//            Icon(
//                imageVector = if (isRecording) Icons.Filled.MicOff else Icons.Filled.Mic,
//                contentDescription = null,
//                tint = Color.White,
//                modifier = Modifier.size(36.dp)
//            )
//        }

//        Spacer(modifier = Modifier.height(20.dp))
//
//        // 模拟切换连接状态
//        Text(
//            text = if (isConnected) "已连接" else "未连接",
//            modifier = Modifier.clickable {
//                isConnected = !isConnected
//            },
//            color = MaterialTheme.colorScheme.primary
//        )
    }
}