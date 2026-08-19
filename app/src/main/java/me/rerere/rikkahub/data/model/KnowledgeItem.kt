package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.db.entity.KnowledgeEntity
import kotlin.uuid.Uuid

@Serializable
data class KnowledgeItem(
    val id: Uuid = Uuid.random(),
    val title: String,
    val content: String,
    val category: KnowledgeCategory = KnowledgeCategory.STUDY,
    val tags: List<String> = emptyList(),
    val sourceType: KnowledgeSource = KnowledgeSource.MANUAL,
    val sourcePath: String? = null,
    val sourceMetadata: String? = null,
    val embedding: String? = null,
    val usageCount: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class KnowledgeCategory(val dbValue: String) {
    STUDY("study"),
    WORK("work"),
    PERSONAL("personal"),
    PROJECT("project"),
    REFERENCE("reference"),
    CODE("code"),
    DESIGN("design");

    companion object {
        fun fromDb(value: String): KnowledgeCategory =
            entries.firstOrNull { it.dbValue == value } ?: STUDY
    }
}

@Serializable
enum class KnowledgeSource(val dbValue: String) {
    MANUAL("manual"),
    LOCAL("local"),
    CLOUD("cloud"),
    IMPORT("import");

    companion object {
        fun fromDb(value: String): KnowledgeSource =
            entries.firstOrNull { it.dbValue == value } ?: MANUAL
    }
}

fun KnowledgeEntity.toItem(): KnowledgeItem = KnowledgeItem(
    id = Uuid.parse(id),
    title = title,
    content = content,
    category = KnowledgeCategory.fromDb(category),
    tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    sourceType = KnowledgeSource.fromDb(sourceType),
    sourcePath = sourcePath,
    sourceMetadata = sourceMetadata,
    embedding = embedding,
    usageCount = usageCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun KnowledgeItem.toEntity(): KnowledgeEntity = KnowledgeEntity(
    id = id.toString(),
    title = title,
    content = content,
    category = category.dbValue,
    tags = tags.joinToString(","),
    sourceType = sourceType.dbValue,
    sourcePath = sourcePath,
    sourceMetadata = sourceMetadata,
    embedding = embedding,
    usageCount = usageCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
