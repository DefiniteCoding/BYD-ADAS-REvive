package com.definitecoding.bydadasrevive.shell

import com.definitecoding.bydadasrevive.adb.AdbConnection
import com.definitecoding.bydadasrevive.adb.AdbKeyPair
import com.definitecoding.bydadasrevive.adb.ShellResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ShellState {
    data object Disconnected : ShellState
    data object Connecting : ShellState
    data class Connected(val banner: String) : ShellState
    data class Failed(val message: String) : ShellState
}

/**
 * Owns the single adb connection the whole flow runs on. Commands are serialised
 * because one socket carries them all.
 */
class ShellChannel(filesDir: File) {

    private val keyPair by lazy { AdbKeyPair.loadOrCreate(filesDir) }
    private val lock = Mutex()
    private var connection: AdbConnection? = null

    private val _state = MutableStateFlow<ShellState>(ShellState.Disconnected)
    val state: StateFlow<ShellState> = _state.asStateFlow()

    val isConnected: Boolean get() = _state.value is ShellState.Connected

    suspend fun connect(
        host: String = AdbConnection.DEFAULT_HOST,
        port: Int = AdbConnection.DEFAULT_PORT,
    ): Result<String> = lock.withLock {
        connection?.close()
        connection = null
        _state.value = ShellState.Connecting

        withContext(Dispatchers.IO) {
            runCatching { AdbConnection.connect(host = host, port = port, keyPair = keyPair) }
        }.map { opened ->
            connection = opened
            _state.value = ShellState.Connected(opened.banner)
            opened.banner
        }.onFailure { error ->
            _state.value = ShellState.Failed(error.message ?: error.javaClass.simpleName)
        }
    }

    suspend fun run(command: String): Result<ShellResult> = lock.withLock {
        val active = connection
            ?: return Result.failure(IllegalStateException("No adb connection"))

        withContext(Dispatchers.IO) {
            runCatching { active.shell(command) }
        }.onFailure { error ->
            // A dead socket must not be reused; force a visible reconnect.
            active.close()
            connection = null
            _state.value = ShellState.Failed(error.message ?: error.javaClass.simpleName)
        }
    }

    fun disconnect() {
        connection?.close()
        connection = null
        _state.value = ShellState.Disconnected
    }
}
