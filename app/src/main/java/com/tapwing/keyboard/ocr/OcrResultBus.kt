package com.tapwing.keyboard.ocr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object OcrResultBus {
    private val _pendingText = MutableStateFlow<String?>(null)
    val pendingText: StateFlow<String?> = _pendingText.asStateFlow()

    fun publish(text: String) {
        val cleaned = text.trim()
        if (cleaned.isNotEmpty()) {
            _pendingText.value = cleaned
        }
    }

    fun consume(): String? {
        val current = _pendingText.value
        _pendingText.value = null
        return current
    }
}
