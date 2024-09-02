package com.rs.s2s

/**
 * description
 * @author whs
 * @date 2024/9/1
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isVoice: Boolean = false
)
