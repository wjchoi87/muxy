package com.muxy.app.ui.connect

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.data.DeviceCredentials
import com.muxy.app.data.DeviceCredentialsStore
import com.muxy.app.data.SavedDevice
import com.muxy.app.data.SavedDevicesStore
import com.muxy.app.data.SessionRepository
import com.muxy.app.model.AuthenticateDeviceParams
import com.muxy.app.model.PairDeviceParams
import com.muxy.app.model.TaggedValue
import com.muxy.app.model.authenticateDeviceRequest
import com.muxy.app.model.decodePairingResult
import com.muxy.app.model.pairDeviceRequest
import com.muxy.app.net.MuxyClient
import com.muxy.app.net.newRequestId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class AwaitingApproval(val deviceName: String) : ConnectionState()
    data class Connected(val deviceName: String, val clientID: String) : ConnectionState()
    data class Error(val message: String, val technicalDetails: String) : ConnectionState()
}

class ConnectionViewModel(app: Application) : AndroidViewModel(app) {
    private val credentialsStore = DeviceCredentialsStore(app)
    private val devicesStore = SavedDevicesStore(app)
    private val client = MuxyClient()
    val session = SessionRepository(client, viewModelScope)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _savedDevices = MutableStateFlow(devicesStore.load())
    val savedDevices: StateFlow<List<SavedDevice>> = _savedDevices.asStateFlow()

    private val androidDeviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    private var lastDevice: SavedDevice? = null

    fun addDevice(name: String, host: String, port: Int) {
        val cleanName = name.ifBlank { "Mac" }
        val updated = (listOf(SavedDevice(cleanName, host, port)) +
                _savedDevices.value.filterNot { it.host == host && it.port == port })
        _savedDevices.value = updated
        devicesStore.save(updated)
    }

    fun removeDevice(device: SavedDevice) {
        val updated = _savedDevices.value.filterNot { it.id == device.id }
        _savedDevices.value = updated
        devicesStore.save(updated)
    }

    fun connect(device: SavedDevice) {
        lastDevice = device
        viewModelScope.launch { runConnection(device) }
    }

    fun reconnect() {
        lastDevice?.let { connect(it) }
    }

    fun disconnect() {
        client.disconnect()
        session.stopObserving()
        _state.value = ConnectionState.Disconnected
    }

    private suspend fun runConnection(device: SavedDevice) {
        _state.value = ConnectionState.Connecting(device.name)
        val credentials = credentialsStore.load()
        client.connect(device.host, device.port)
        delay(500)

        if (!authenticateOrPair(credentials, device.name)) return

        // Post-pairing: start observing events and refresh projects.
        session.startObserving()
        session.refreshProjects()
    }

    private suspend fun authenticateOrPair(credentials: DeviceCredentials, deviceLabel: String): Boolean {
        val authResp = try {
            client.send(
                authenticateDeviceRequest(
                    newRequestId(),
                    AuthenticateDeviceParams(
                        deviceID = credentials.deviceID.toString(),
                        deviceName = androidDeviceName,
                        token = credentials.token,
                    ),
                ),
                timeout = 10.seconds,
            )
        } catch (t: Throwable) {
            fail("Could not reach device", "authenticateDevice failed: ${t.message}")
            return false
        }

        val authError = authResp.error
        if (authError == null) return finishPairing(authResp.result, deviceLabel)
        if (authError.code != 401) {
            fail("Authentication failed", "code ${authError.code}: ${authError.message}")
            return false
        }

        _state.value = ConnectionState.AwaitingApproval(deviceLabel)
        val pairResp = try {
            client.send(
                pairDeviceRequest(
                    newRequestId(),
                    PairDeviceParams(
                        deviceID = credentials.deviceID.toString(),
                        deviceName = androidDeviceName,
                        token = credentials.token,
                    ),
                ),
                timeout = 120.seconds,
            )
        } catch (t: Throwable) {
            fail("Could not finish pairing", "pairDevice failed: ${t.message}")
            return false
        }

        val pairError = pairResp.error
        if (pairError != null) {
            val msg = if (pairError.code == 403) "Approval denied on Mac" else "Could not finish pairing"
            fail(msg, "code ${pairError.code}: ${pairError.message}")
            return false
        }
        return finishPairing(pairResp.result, deviceLabel)
    }

    private fun finishPairing(result: TaggedValue?, deviceLabel: String): Boolean {
        val pairing = decodePairingResult(result)
        if (pairing == null) {
            fail("Could not finish pairing", "expected 'pairing' result, got '${result?.type}'")
            return false
        }
        _state.value = ConnectionState.Connected(deviceLabel, pairing.clientID)
        session.setMyClientID(pairing.clientID)
        if (pairing.themeFg != null && pairing.themeBg != null) {
            session.applyInitialTheme(pairing.themeFg, pairing.themeBg, pairing.themePalette ?: emptyList())
        }
        return true
    }

    private fun fail(userMessage: String, technical: String) {
        client.disconnect()
        session.stopObserving()
        _state.value = ConnectionState.Error(userMessage, technical)
    }

    override fun onCleared() {
        client.disconnect()
        session.stopObserving()
        super.onCleared()
    }
}
