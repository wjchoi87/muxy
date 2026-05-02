package com.muxy.app.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ProjectDTO(
    val id: String,
    val name: String,
    val path: String,
    val sortOrder: Int,
    val createdAt: String,
    val icon: String? = null,
    val logo: String? = null,
    val iconColor: String? = null,
)

@Serializable
data class WorktreeDTO(
    val id: String,
    val name: String,
    val path: String,
    val branch: String? = null,
    val isPrimary: Boolean,
    val canBeRemoved: Boolean? = null,
    val createdAt: String,
)

@Serializable
enum class TabKindDTO {
    @SerialName("terminal") TERMINAL,
    @SerialName("vcs") VCS,
    @SerialName("editor") EDITOR,
    @SerialName("diffViewer") DIFF_VIEWER,
}

@Serializable
data class TabDTO(
    val id: String,
    val kind: TabKindDTO,
    val title: String,
    val isPinned: Boolean,
    val paneID: String? = null,
)

@Serializable
data class TabAreaDTO(
    val id: String,
    val projectPath: String,
    val tabs: List<TabDTO>,
    val activeTabID: String? = null,
)

@Serializable
enum class SplitDirectionDTO {
    @SerialName("horizontal") HORIZONTAL,
    @SerialName("vertical") VERTICAL,
}

sealed class SplitNodeDTO {
    data class TabArea(val area: TabAreaDTO) : SplitNodeDTO()
    data class Split(val branch: SplitBranchDTO) : SplitNodeDTO()
}

@Serializable
data class SplitBranchDTO(
    val id: String,
    val direction: SplitDirectionDTO,
    val ratio: Double,
    val first: @Serializable(with = SplitNodeSerializer::class) SplitNodeDTO,
    val second: @Serializable(with = SplitNodeSerializer::class) SplitNodeDTO,
)

object SplitNodeSerializer : KSerializer<SplitNodeDTO> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SplitNodeDTO") {
        element<String>("type")
        element<JsonObject>("tabArea", isOptional = true)
        element<JsonObject>("split", isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: SplitNodeDTO) {
        val obj = when (value) {
            is SplitNodeDTO.TabArea -> JsonObject(
                mapOf(
                    "type" to JsonPrimitive("tabArea"),
                    "tabArea" to MuxyJson.encodeToJsonElement(TabAreaDTO.serializer(), value.area),
                )
            )
            is SplitNodeDTO.Split -> JsonObject(
                mapOf(
                    "type" to JsonPrimitive("split"),
                    "split" to MuxyJson.encodeToJsonElement(SplitBranchDTO.serializer(), value.branch),
                )
            )
        }
        encoder.encodeSerializableValue(JsonElement.serializer(), obj)
    }

    override fun deserialize(decoder: Decoder): SplitNodeDTO {
        val obj = decoder.decodeSerializableValue(JsonElement.serializer()).jsonObject
        return when (val type = obj["type"]?.jsonPrimitive?.contentOrNull) {
            "tabArea" -> SplitNodeDTO.TabArea(
                MuxyJson.decodeFromJsonElement(TabAreaDTO.serializer(), obj["tabArea"]!!)
            )
            "split" -> SplitNodeDTO.Split(
                MuxyJson.decodeFromJsonElement(SplitBranchDTO.serializer(), obj["split"]!!)
            )
            else -> error("Unknown SplitNode type: $type")
        }
    }
}

@Serializable
data class WorkspaceDTO(
    val projectID: String,
    val worktreeID: String,
    val focusedAreaID: String? = null,
    val root: @Serializable(with = SplitNodeSerializer::class) SplitNodeDTO,
)

@Serializable
data class VCSBranchesDTO(
    val current: String,
    val locals: List<String>,
    val remotes: List<String> = emptyList(),
)

@Serializable
data class ProjectLogoDTO(
    val projectID: String,
    val pngData: String,
)

@Serializable
data class DeviceThemeEventDTO(
    val fg: Long,
    val bg: Long,
    val palette: List<Long>? = null,
)

@Serializable
data class TabChangeEventDTO(
    val projectID: String,
    val areaID: String,
    val tab: TabDTO,
    val changeKind: ChangeKind,
) {
    @Serializable
    enum class ChangeKind {
        @SerialName("created") CREATED,
        @SerialName("closed") CLOSED,
        @SerialName("selected") SELECTED,
        @SerialName("titleChanged") TITLE_CHANGED,
    }
}

@Serializable
data class PaneOwnershipEventDTO(
    val paneID: String,
    val owner: JsonElement,
)

fun SplitNodeDTO.collectAreas(): List<TabAreaDTO> = when (this) {
    is SplitNodeDTO.TabArea -> listOf(area)
    is SplitNodeDTO.Split -> branch.first.collectAreas() + branch.second.collectAreas()
}
