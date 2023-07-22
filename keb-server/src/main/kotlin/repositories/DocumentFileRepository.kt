package keb.server.repositories

import keb.server.entities.DocumentEntity
import keb.server.entities.DocumentFileEntity
import keb.server.entities.FileAddress
import keb.server.repositories.orm.mapToDocumentFile
import keb.server.util.debug
import keb.server.util.logger
import org.springframework.data.r2dbc.core.*
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import org.springframework.stereotype.Component

interface DocumentFileRepository {
    suspend fun save(input: DocumentFileEntity): DocumentFileEntity
    suspend fun updateFileAddress(target: Long, change: FileAddress)
    suspend fun findById(target: Long): DocumentFileEntity?
    suspend fun findByFileAddress(input: FileAddress): DocumentFileEntity?
    suspend fun deleteById(target: Long): Long
    suspend fun deleteAll(): Long
}

@Component
class DocumentFileRepositoryImpl(private val template: R2dbcEntityTemplate) : RepositoryBase(), DocumentFileRepository {
    private val logger = logger()

    override suspend fun save(input: DocumentFileEntity): DocumentFileEntity {
        return template.insert<DocumentFileEntity>().usingAndAwait(input)
    }

    override suspend fun updateFileAddress(target: Long, change: FileAddress) {
        logger.debug { "Update operation for:$target with new address :$change" }
        runSingleUpdate {
            template.update<DocumentEntity>()
                .matching(idQueryMatch(target))
                .applyAndAwait(Update.update("fileAddress", change))
        }
    }

    override suspend fun findById(target: Long): DocumentFileEntity? {
        return runSingleSelect {
            val result = template.databaseClient.sql(
                "SELECT * FROM documentFile a LEFT JOIN document d ON a.document_id = d.id WHERE a.id = :target"
            ).bind("target", target.toString())
            mapToDocumentFile(result)
        }
    }

    override suspend fun findByFileAddress(input: FileAddress): DocumentFileEntity? {
        return runSingleSelect {
            TODO()
        }
    }

    override suspend fun deleteById(target: Long): Long {
        logger.debug { "Delete operation for target:$target" }
        return template.delete<DocumentEntity>().matching(idQueryMatch(target)).allAndAwait()
    }

    override suspend fun deleteAll(): Long {
        return template.delete<DocumentFileEntity>().allAndAwait()
    }

    private fun idQueryMatch(target: Long): Query {
        return Query.query(Criteria.where("id").`is`(target))
    }
}
