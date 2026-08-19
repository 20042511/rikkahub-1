package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.KnowledgeDAO
import me.rerere.rikkahub.data.db.entity.KnowledgeEntity
import me.rerere.rikkahub.data.db.fts.KnowledgeFtsManager
import me.rerere.rikkahub.data.db.fts.KnowledgeSearchResult
import me.rerere.rikkahub.data.model.KnowledgeItem
import me.rerere.rikkahub.data.model.toEntity
import me.rerere.rikkahub.data.model.toItem

class KnowledgeRepository(
    private val dao: KnowledgeDAO,
    private val ftsManager: KnowledgeFtsManager,
) {
    fun getAllFlow(): Flow<List<KnowledgeEntity>> = dao.getAllFlow()

    fun getByCategoryFlow(category: String): Flow<List<KnowledgeEntity>> =
        dao.getByCategoryFlow(category)

    suspend fun getAll(): List<KnowledgeItem> = dao.getAll().map { it.toItem() }

    suspend fun getById(id: String): KnowledgeItem? = dao.getById(id)?.toItem()

    suspend fun add(item: KnowledgeItem) {
        val entity = item.toEntity()
        dao.insert(entity)
        ftsManager.index(entity)
    }

    suspend fun update(item: KnowledgeItem) {
        val entity = item.toEntity()
        dao.update(entity)
        ftsManager.index(entity)
    }

    suspend fun delete(id: String) {
        dao.delete(id)
        ftsManager.delete(id)
    }

    suspend fun search(keyword: String, limit: Int = 20): List<KnowledgeSearchResult> =
        ftsManager.search(keyword, limit)

    suspend fun getByCategory(category: String): List<KnowledgeItem> =
        dao.getByCategory(category).map { it.toItem() }

    suspend fun getByTag(tag: String): List<KnowledgeItem> =
        dao.getByTag(tag).map { it.toItem() }

    suspend fun getPopular(limit: Int): List<KnowledgeItem> =
        dao.getPopular(limit).map { it.toItem() }

    suspend fun getCategoryCounts(): List<Pair<String, Int>> =
        dao.getAll().groupingBy { it.category }.eachCount().toList().sortedByDescending { it.second }

    suspend fun getAllTags(): List<String> =
        dao.getAll()
            .flatMap { it.tags.split(',').map(String::trim).filter(String::isNotEmpty) }
            .distinct()
            .sorted()

    suspend fun bumpUsage(id: String) {
        dao.bumpUsage(id, System.currentTimeMillis())
    }
}
