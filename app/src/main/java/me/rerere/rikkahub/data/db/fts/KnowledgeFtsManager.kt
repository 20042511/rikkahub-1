package me.rerere.rikkahub.data.db.fts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.KnowledgeEntity

data class KnowledgeSearchResult(
    val id: String,
    val title: String,
    val category: String,
    val snippet: String,
)

class KnowledgeFtsManager(private val database: AppDatabase) {

    private val db get() = database.openHelper.writableDatabase

    suspend fun index(entity: KnowledgeEntity) = withContext(Dispatchers.IO) {
        db.execSQL("DELETE FROM knowledge_fts WHERE knowledge_id = ?", arrayOf(entity.id))
        if (entity.title.isNotBlank() || entity.content.isNotBlank()) {
            db.execSQL(
                "INSERT INTO knowledge_fts(knowledge_id, title, content, category, tags) VALUES (?, ?, ?, ?, ?)",
                arrayOf(
                    entity.id,
                    entity.title,
                    entity.content,
                    entity.category,
                    entity.tags,
                )
            )
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        db.execSQL("DELETE FROM knowledge_fts WHERE knowledge_id = ?", arrayOf(id))
    }

    suspend fun search(keyword: String, limit: Int = 20): List<KnowledgeSearchResult> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<KnowledgeSearchResult>()
            val cursor = db.query(
                """
                SELECT knowledge_id, title, category,
                       simple_snippet(knowledge_fts, 2, '[', ']', '...', 30) AS snippet
                FROM knowledge_fts
                WHERE knowledge_fts MATCH jieba_query(?)
                ORDER BY rank
                LIMIT ?
                """.trimIndent(),
                arrayOf(keyword, limit.toString())
            )
            cursor.use {
                while (it.moveToNext()) {
                    results.add(
                        KnowledgeSearchResult(
                            id = it.getString(0),
                            title = it.getString(1),
                            category = it.getString(2),
                            snippet = it.getString(3),
                        )
                    )
                }
            }
            results
        }
}
