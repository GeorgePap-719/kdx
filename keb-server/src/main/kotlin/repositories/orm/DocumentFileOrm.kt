package keb.server.repositories.orm

import keb.server.entities.DocumentEntity
import keb.server.entities.DocumentFileEntity
import keb.server.entities.FileAddress
import keb.server.util.Debugger
import org.springframework.r2dbc.core.DatabaseClient
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

suspend fun mapToDocumentFile(queryResult: DatabaseClient.GenericExecuteSpec): DocumentFileEntity? {
    return buildDocumentFile {
        queryResult.map { row, metadata ->
            check(metadata.columnMetadatas.size == 5) {
                "size should be equal to all fields of DocumentFile plus Document"
            }
            Debugger.debug { "row:$row,\n metadata:$metadata" }
        }
    }
}

@OptIn(ExperimentalContracts::class)
private inline fun buildDocumentFile(action: DocumentFileEntityBuilder.() -> Unit): DocumentFileEntity? {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    val builder = DocumentFileEntityBuilder()
    builder.action()
    return builder.build()
}

private class DocumentFileEntityBuilder {
    var id: Long? = null
    var document: DocumentEntity? = null
    var fileAddress: FileAddress? = null

    var isEmpty = true

    fun build(): DocumentFileEntity? {
        if (isEmpty) return null
        val id = requireNotNull(id)
        val document = requireNotNull(document)
        val fileAddress = requireNotNull(fileAddress)
        return DocumentFileEntity(id, document, fileAddress)
    }
}