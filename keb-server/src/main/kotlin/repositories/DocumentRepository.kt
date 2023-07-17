package keb.server.repositories

import keb.server.dto.Text
import keb.server.entities.DocumentEntity
import keb.server.utils.logger
import org.springframework.data.r2dbc.core.*
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import org.springframework.stereotype.Component

interface DocumentRepository {
    suspend fun save(input: DocumentEntity): DocumentEntity
    suspend fun append(target: DocumentEntity, input: Text): Int
    suspend fun removeText(target: DocumentEntity, input: Text): Int
    suspend fun findByName(target: String): DocumentEntity?
    suspend fun remove(target: String): Int
    suspend fun deleteAll(): Int
}

@Component
class DocumentRepositoryImpl(private val template: R2dbcEntityTemplate) : DocumentRepository {
    private val logger = logger()

    override suspend fun save(input: DocumentEntity): DocumentEntity {
        return template.insert<DocumentEntity>().usingAndAwait(input)
    }

    // naive impl for now
    // required to check beforehand if document exists
    override suspend fun append(target: DocumentEntity, input: Text): Int {
        val updatedText = target.text + input
        logger.info("preparing to update document with $updatedText")
        return template.update<DocumentEntity>()
            .matching(documentQueryMatch(target.name))
            .applyAndAwait(Update.update("text", updatedText.value)).toInt()
    }

    override suspend fun removeText(target: DocumentEntity, input: Text): Int {
        val updatedText = target.text - input
        logger.info("preparing to update document with:$updatedText")
        return template.update<DocumentEntity>()
            .matching(documentQueryMatch(target.name))
            .applyAndAwait(Update.update("text", updatedText.value)).toInt()
    }

    override suspend fun findByName(target: String): DocumentEntity? {
        return template.select<DocumentEntity>().matching(documentQueryMatch(target)).awaitFirstOrNull()
    }

    override suspend fun remove(target: String): Int {
        return template.delete<DocumentEntity>().matching(documentQueryMatch(target)).allAndAwait().toInt()
    }

    override suspend fun deleteAll(): Int {
        return template.delete<DocumentEntity>().allAndAwait().toInt()
    }

    private fun documentQueryMatch(target: String): Query {
        return Query.query(Criteria.where("name").`is`(target))
    }
}

