package com.example.trencadisapp.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ClockSource {
    val isConnected: StateFlow<Boolean>
    val bpmFlow: Flow<Float>
    val beatFlow: Flow<Int>
    val keyFlow: Flow<Int>
    val scaleFlow: Flow<Int>
    fun connect()
    fun disconnect()
}
