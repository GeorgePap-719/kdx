package keb.server.services

import keb.Document
import keb.Text
import keb.server.entities.toDocument
import keb.server.entities.toEntity
import keb.server.repositories.DocumentRepository
import org.springframework.stereotype.Service

interface DocumentService {
    suspend fun read(target: String): Document?
    suspend fun create(input: Document): Document
    suspend fun append(target: String, input: String): Int
    suspend fun removeText(target: String, input: String): Int
    suspend fun remove(target: String): Int
}

@Service
class DocumentServiceImpl(private val documentRepository: DocumentRepository) : DocumentService {
    override suspend fun read(target: String): Document? {
        return documentRepository.findByName(target)?.toDocument()
    }

    override suspend fun create(input: Document): Document {
        return documentRepository.save(input.toEntity()).toDocument()
    }

    override suspend fun append(target: String, input: String): Int {
        val document = documentRepository.findByName(target) ?: return 0
        return documentRepository.append(document, Text(input))
    }

    override suspend fun removeText(target: String, input: String): Int {
        val document = documentRepository.findByName(target) ?: return 0
        return documentRepository.removeText(document, Text(input))
    }

    override suspend fun remove(target: String): Int {
        return documentRepository.remove(target)
    }
}