package com.muxy.app

import com.muxy.app.model.MuxyJson
import com.muxy.app.model.SplitNodeDTO
import com.muxy.app.model.SplitNodeSerializer
import com.muxy.app.model.WorkspaceDTO
import com.muxy.app.model.collectAreas
import com.muxy.app.model.decodeBranches
import com.muxy.app.model.decodeMessage
import com.muxy.app.model.MuxyMessage
import com.muxy.app.model.decodeProjects
import com.muxy.app.model.decodeWorkspace
import com.muxy.app.model.listProjectsRequest
import com.muxy.app.model.encodeMessage
import com.muxy.app.model.selectProjectRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2Test {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun listProjectsRequestHasNoParams() {
        val text = encodeMessage(listProjectsRequest("r1"))
        val payload = json.parseToJsonElement(text).jsonObject["payload"]!!.jsonObject
        assertEquals("listProjects", payload["method"]!!.jsonPrimitive.content)
        assertNull(payload["params"])
    }

    @Test
    fun selectProjectRequestWrapsParamsAsTaggedValue() {
        val text = encodeMessage(selectProjectRequest("r2", "p-1"))
        val payload = json.parseToJsonElement(text).jsonObject["payload"]!!.jsonObject
        val params = payload["params"]!!.jsonObject
        assertEquals("selectProject", params["type"]!!.jsonPrimitive.content)
        assertEquals("p-1", params["value"]!!.jsonObject["projectID"]!!.jsonPrimitive.content)
    }

    @Test
    fun decodesProjectsResultAndIgnoresUnknownFields() {
        val raw = """
            {"type":"response","payload":{"id":"x","result":{"type":"projects","value":[
              {"id":"a","name":"App","path":"/a","sortOrder":0,"createdAt":"2024-01-01T00:00:00Z","iconColor":"blue","futureField":1}
            ]}}}
        """.trimIndent()
        val resp = (decodeMessage(raw) as MuxyMessage.Response).payload
        val projects = decodeProjects(resp.result)
        assertNotNull(projects)
        assertEquals("App", projects!!.first().name)
        assertEquals("blue", projects.first().iconColor)
    }

    @Test
    fun decodesWorkspaceWithNestedSplit() {
        val raw = """
            {"type":"response","payload":{"id":"x","result":{"type":"workspace","value":{
              "projectID":"p","worktreeID":"w","focusedAreaID":"a1",
              "root":{"type":"split","split":{"id":"s","direction":"horizontal","ratio":0.5,
                "first":{"type":"tabArea","tabArea":{"id":"a1","projectPath":"/p","tabs":[
                  {"id":"t1","kind":"terminal","title":"~","isPinned":false,"paneID":"pane-1"}
                ],"activeTabID":"t1"}},
                "second":{"type":"tabArea","tabArea":{"id":"a2","projectPath":"/p","tabs":[],"activeTabID":null}}
              }}
            }}}}
        """.trimIndent()
        val resp = (decodeMessage(raw) as MuxyMessage.Response).payload
        val ws = decodeWorkspace(resp.result)!!
        assertEquals("a1", ws.focusedAreaID)
        val areas = ws.root.collectAreas()
        assertEquals(2, areas.size)
        assertEquals("t1", areas[0].activeTabID)
    }

    @Test
    fun decodesBranchesResult() {
        val raw = """
            {"type":"response","payload":{"id":"x","result":{"type":"vcsBranches","value":{
              "current":"main","locals":["main","feature/x"],"remotes":["origin/main"]
            }}}}
        """.trimIndent()
        val resp = (decodeMessage(raw) as MuxyMessage.Response).payload
        val branches = decodeBranches(resp.result)!!
        assertEquals("main", branches.current)
        assertTrue(branches.locals.contains("feature/x"))
    }
}
