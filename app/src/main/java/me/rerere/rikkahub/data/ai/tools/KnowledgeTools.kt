package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.KnowledgeCategory
import me.rerere.rikkahub.data.model.KnowledgeItem
import me.rerere.rikkahub.data.repository.KnowledgeRepository

fun buildKnowledgeTools(
    json: Json,
    repository: KnowledgeRepository,
): List<Tool> = listOf(
    Tool(
        name = "knowledge_search",
        description = """
            Searches the user's personal knowledge base and returns matching entries.
            Use `action` to control the operation:
            - "search": full-text search with `query` and optional `limit`
            - "get_by_category": list entries of a category (study/work/personal/project/reference/code/design)
            - "get_by_tag": list entries that contain a tag
            - "get_popular": list the most used entries, optional `limit`
            If nothing matches, return an empty list. Do not invent content that is not in the result.
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put(
                            "enum",
                            buildJsonArray {
                                add("search")
                                add("get_by_category")
                                add("get_by_tag")
                                add("get_popular")
                            }
                        )
                        put("description", "Operation to perform")
                    })
                    put("query", buildJsonObject {
                        put("type", "string")
                        put("description", "Search keyword (required when action=search)")
                    })
                    put("category", buildJsonObject {
                        put("type", "string")
                        put(
                            "enum",
                            buildJsonArray {
                                KnowledgeCategory.entries.forEach { add(it.dbValue) }
                            }
                        )
                        put("description", "Category filter (required when action=get_by_category)")
                    })
                    put("tag", buildJsonObject {
                        put("type", "string")
                        put("description", "Tag filter (required when action=get_by_tag)")
                    })
                    put("limit", buildJsonObject {
                        put("type", "integer")
                        put("description", "Max result count, default 5, max 50")
                    })
                },
                required = listOf("action")
            )
        },
        execute = { element ->
            val params = element.jsonObject
            val action = params["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
            val payload = when (action) {
                "search" -> {
                    val query = params["query"]?.jsonPrimitive?.contentOrNull ?: error("query is required")
                    val limit = (params["limit"]?.jsonPrimitive?.intOrNull ?: 5).coerceIn(1, 50)
                    buildJsonArray {
                        repository.search(query, limit).forEach { result ->
                            add(
                                buildJsonObject {
                                    put("id", result.id)
                                    put("title", result.title)
                                    put("category", result.category)
                                    put("snippet", result.snippet)
                                }
                            )
                        }
                    }
                }

                "get_by_category" -> {
                    val category = params["category"]?.jsonPrimitive?.contentOrNull
                        ?: error("category is required")
                    val items = repository.getByCategory(category)
                    items.forEach { repository.bumpUsage(it.id.toString()) }
                    buildJsonArray {
                        items.forEach { add(json.encodeToJsonElement(KnowledgeItem.serializer(), it)) }
                    }
                }

                "get_by_tag" -> {
                    val tag = params["tag"]?.jsonPrimitive?.contentOrNull ?: error("tag is required")
                    val items = repository.getByTag(tag)
                    items.forEach { repository.bumpUsage(it.id.toString()) }
                    buildJsonArray {
                        items.forEach { add(json.encodeToJsonElement(KnowledgeItem.serializer(), it)) }
                    }
                }

                "get_popular" -> {
                    val limit = (params["limit"]?.jsonPrimitive?.intOrNull ?: 10).coerceIn(1, 50)
                    val items = repository.getPopular(limit)
                    buildJsonArray {
                        items.forEach { add(json.encodeToJsonElement(KnowledgeItem.serializer(), it)) }
                    }
                }

                else -> error("unknown action: $action, must be one of [search, get_by_category, get_by_tag, get_popular]")
            }
            listOf(UIMessagePart.Text(payload.toString()))
        }
    ),
    Tool(
        name = "knowledge_list_categories",
        description = "Lists all knowledge categories with the number of entries in each.",
        parameters = { null },
        execute = {
            val payload = buildJsonObject {
                put(
                    "categories",
                    buildJsonArray {
                        repository.getCategoryCounts().forEach { (category, count) ->
                            add(
                                buildJsonObject {
                                    put("category", category)
                                    put("count", count)
                                }
                            )
                        }
                    }
                )
            }
            listOf(UIMessagePart.Text(payload.toString()))
        }
    ),
    Tool(
        name = "knowledge_list_tags",
        description = "Lists all tags in the user's knowledge base.",
        parameters = { null },
        execute = {
            val payload = buildJsonObject {
                put(
                    "tags",
                    buildJsonArray {
                        repository.getAllTags().forEach { add(JsonPrimitive(it)) }
                    }
                )
            }
            listOf(UIMessagePart.Text(payload.toString()))
        }
    ),
)
