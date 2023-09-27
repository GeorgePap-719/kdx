package keb.server.dto

import keb.server.entities.DocumentFileEntity
import keb.server.entities.FileAddress
import kotlinx.serialization.Serializable

@Serializable
data class CreateDocumentFile(val document: CreateDocument, val fileAddress: FileAddress)

fun CreateDocumentFile.toDocumentFile(): DocumentFile = DocumentFile(0, document.toDocument(), fileAddress)

@Serializable
data class DocumentFile(val id: Long, val document: Document, val fileAddress: FileAddress)

fun DocumentFile.toEntity(): DocumentFileEntity = DocumentFileEntity(id, document.toEntity(), fileAddress)
fun DocumentFileEntity.toDocumentFile(): DocumentFile = DocumentFile(id, document.toDocument(), fileAddress)