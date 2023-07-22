package keb.server.dto

import keb.server.entities.DocumentEntity
import kotlinx.serialization.Serializable

@Serializable
data class Document(val id: Long, val text: Text)

fun Document.toEntity(): DocumentEntity = DocumentEntity(id, text)
fun DocumentEntity.toDocument(): Document = Document(id, text)