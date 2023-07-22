package keb.server.services

import keb.server.dto.*
import keb.server.entities.DocumentEntity
import keb.server.entities.FileAddress
import keb.server.repositories.DocumentFileRepository
import keb.server.repositories.DocumentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface DocumentFileService {
    suspend fun create(input: CreateDocumentFile): DocumentFile
    suspend fun read(target: FileAddress): DocumentFile?
    suspend fun read(target: Long): DocumentFile?
    suspend fun rename(target: Long, newAddress: FileAddress)
    suspend fun changeText(target: Long, newText: Text)
}

@Service
class DocumentFileServiceImpl(
    private val documentFileRepository: DocumentFileRepository,
    private val documentRepository: DocumentRepository
) : DocumentFileService {
    override suspend fun create(input: CreateDocumentFile): DocumentFile {
        val newDocumentFile = input.toDocumentFile().toEntity()
        return documentFileRepository.save(newDocumentFile).toDocumentFile()
    }

    override suspend fun read(target: FileAddress): DocumentFile? {
        return documentFileRepository.findByFileAddress(target)?.toDocumentFile()
    }

    override suspend fun read(target: Long): DocumentFile? {
        return documentFileRepository.findById(target)?.toDocumentFile()
    }

    @Transactional
    override suspend fun rename(target: Long, newAddress: FileAddress) {
        documentFileRepository.updateFileAddress(target, newAddress)
    }

    // id of the document, not file
    @Transactional
    override suspend fun changeText(target: Long, newText: Text) {
        val newDocument = DocumentEntity(target, newText)
        documentRepository.update(newDocument)
    }
}