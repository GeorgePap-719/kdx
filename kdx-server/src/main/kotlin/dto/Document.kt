package kdx.server.dto

import entities.DocumentEntity
import kotlinx.serialization.Serializable

@Serializable
data class CreateDocument(val text: Text)

fun CreateDocument.toDocument(): Document = Document(0, text)

@Serializable
data class Document(val id: Long, val text: Text)

fun Document.toEntity(): DocumentEntity = DocumentEntity(id, text)
fun DocumentEntity.toDocument(): Document = Document(id, text)