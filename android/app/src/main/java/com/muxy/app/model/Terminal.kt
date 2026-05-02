package com.muxy.app.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class TakeOverPaneParams(val paneID: String, val cols: Int, val rows: Int)
@Serializable data class ReleasePaneParams(val paneID: String)
@Serializable data class TerminalInputParams(val paneID: String, val bytes: String) // base64
@Serializable data class TerminalResizeParams(val paneID: String, val cols: Int, val rows: Int)
@Serializable data class TerminalScrollParams(val paneID: String, val deltaX: Double, val deltaY: Double, val precise: Boolean)

@Serializable data class TerminalOutputEventDTO(val paneID: String, val bytes: String)

private inline fun <reified T> tagged(typeName: String, params: T, ser: kotlinx.serialization.KSerializer<T>) =
    TaggedValue(type = typeName, value = MuxyJson.encodeToJsonElement(ser, params))

private fun req(id: String, method: String, params: TaggedValue?) =
    MuxyMessage.Request(MuxyRequest(id = id, method = method, params = params))

fun takeOverPaneRequest(id: String, params: TakeOverPaneParams) =
    req(id, "takeOverPane", tagged("takeOverPane", params, TakeOverPaneParams.serializer()))

fun releasePaneRequest(id: String, params: ReleasePaneParams) =
    req(id, "releasePane", tagged("releasePane", params, ReleasePaneParams.serializer()))

fun terminalInputRequest(id: String, params: TerminalInputParams) =
    req(id, "terminalInput", tagged("terminalInput", params, TerminalInputParams.serializer()))

fun terminalResizeRequest(id: String, params: TerminalResizeParams) =
    req(id, "terminalResize", tagged("terminalResize", params, TerminalResizeParams.serializer()))

fun terminalScrollRequest(id: String, params: TerminalScrollParams) =
    req(id, "terminalScroll", tagged("terminalScroll", params, TerminalScrollParams.serializer()))

fun decodeTerminalOutput(data: TaggedValue?): TerminalOutputEventDTO? {
    if (data == null || data.value == null) return null
    if (data.type != "terminalOutput" && data.type != "terminalSnapshot") return null
    return MuxyJson.decodeFromJsonElement(TerminalOutputEventDTO.serializer(), data.value)
}
