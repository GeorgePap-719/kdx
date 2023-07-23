package keb.server.repositories.orm

import keb.server.entities.DocumentEntity
import keb.server.entities.DocumentFileEntity
import keb.server.entities.FileAddress
import keb.server.util.Debugger
import kotlinx.coroutines.flow.collect
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.flow
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

suspend fun mapToDocumentFile(queryResult: DatabaseClient.GenericExecuteSpec): DocumentFileEntity? {
    return buildDocumentFile {
        var throwable: Throwable? = null
        val fetchSpec = queryResult.map { row, metadata ->
            try {
                println("------------------------ row:$row,\n metadata:$metadata ------------------------ row:")
                check(metadata.columnMetadatas.size == 5) {
                    "size should be equal to all fields of DocumentFile plus Document"
                }
                println("row:$row,\n metadata:$metadata")
                Debugger.debug { "row:$row,\n metadata:$metadata" }
            } catch (e: Throwable) {
                throwable = e
            }
        }
        fetchSpec.flow().collect() // collect all rows
        val throwableSC = throwable // otherwise, smartcast is impossible.
        if (throwableSC != null) {
            // Delegate error, since reactor operators suppress error
            // and do not propagate exceptions like coroutines.
            // Reactor operators usually omit the elements in case of an exception.
            throw throwableSC
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

    val isEmpty: Boolean get() = id == null && document == null && fileAddress == null

    fun build(): DocumentFileEntity? {
        if (isEmpty) return null
        val id = requireNotNull(id)
        val document = requireNotNull(document)
        val fileAddress = requireNotNull(fileAddress)
        return DocumentFileEntity(id, document, fileAddress)
    }
}