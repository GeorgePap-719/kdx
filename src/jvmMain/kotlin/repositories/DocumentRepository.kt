package keb.server.repositories

import keb.Text
import keb.server.entities.DocumentEntity
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
}

@Component
class DocumentRepositoryImpl(private val template: R2dbcEntityTemplate) : DocumentRepository {
    override suspend fun save(input: DocumentEntity): DocumentEntity {
        return template.insert<DocumentEntity>().usingAndAwait(input)
    }

    // naive impl for now
    // required to check beforehand if document exists
    override suspend fun append(target: DocumentEntity, input: Text): Int {
        return template.update<DocumentEntity>()
            .matching(documentQueryMatch(target.name))
            .applyAndAwait(Update.update("text", target.text + input)).toInt()
    }

    override suspend fun removeText(target: DocumentEntity, input: Text): Int {
        return template.update<DocumentEntity>()
            .matching(documentQueryMatch(target.name))
            .applyAndAwait(Update.update("text", target.text - input)).toInt()
    }

    override suspend fun findByName(target: String): DocumentEntity? {
        return template.select<DocumentEntity>().matching(documentQueryMatch(target)).awaitFirstOrNull()
    }

    override suspend fun remove(target: String): Int {
        return template.delete<DocumentEntity>().matching(documentQueryMatch(target)).allAndAwait().toInt()
    }

    private fun documentQueryMatch(target: String): Query {
        return Query.query(Criteria.where("name").`is`(target))
    }
}

