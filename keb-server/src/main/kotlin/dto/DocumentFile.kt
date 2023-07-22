package keb.server.dto

import keb.server.entities.DocumentFileEntity
import keb.server.entities.FileAddress
import kotlinx.serialization.Serializable

@Serializable
data class DocumentFile(val id: Long, val document: Document, val fileAddress: FileAddress)

fun DocumentFile.toEntity(): DocumentFileEntity = DocumentFileEntity(id, document.toEntity(), fileAddress)
fun DocumentFileEntity.toDocumentFile(): DocumentFile = DocumentFile(id, document.toDocument(), fileAddress)