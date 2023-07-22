package keb.server.repositories

import keb.server.entities.DocumentEntity
import keb.server.util.debug
import keb.server.util.logger
import org.springframework.data.r2dbc.core.*
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import org.springframework.stereotype.Component

interface DocumentRepository {
    suspend fun save(input: DocumentEntity): DocumentEntity
    suspend fun update(input: DocumentEntity)
    suspend fun findById(target: Long): DocumentEntity?
    suspend fun deleteById(target: Long)
    suspend fun deleteAll(): Long
}

@Component
class DocumentRepositoryImpl(private val template: R2dbcEntityTemplate) : RepositoryBase(), DocumentRepository {
    private val logger = logger()

    override suspend fun save(input: DocumentEntity): DocumentEntity {
        return template.insert<DocumentEntity>().usingAndAwait(input)
    }

    override suspend fun update(input: DocumentEntity) {
        logger.debug { "Update operation with:$input" }
        runSingleUpdate {
            template.update<DocumentEntity>()
                .matching(idQueryMatch(input.id))
                .applyAndAwait(Update.update("text", input.text))
        }
    }

    override suspend fun findById(target: Long): DocumentEntity? {
        return runSingleSelect { template.select<DocumentEntity>().matching(idQueryMatch(target)).awaitOneOrNull() }
    }

    override suspend fun deleteById(target: Long) {
        logger.debug { "Delete operation for target:$target" }
        runSingleDelete { template.delete<DocumentEntity>().matching(idQueryMatch(target)).allAndAwait() }
    }

    override suspend fun deleteAll(): Long {
        return template.delete<DocumentEntity>().allAndAwait()
    }

    private fun idQueryMatch(target: Long): Query {
        return Query.query(Criteria.where("id").`is`(target))
    }
}