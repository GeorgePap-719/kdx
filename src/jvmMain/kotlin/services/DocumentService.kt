package keb.server.services

import keb.Document

interface DocumentService {
    fun read(): Document
    fun create(input: Document)
    fun append(target: String, input: String)
    fun remove(target: String, input: String)
}

class DocumentServiceImpl : DocumentService {
    override fun read(): Document {
        TODO("Not yet implemented")
    }

    override fun create(input: Document) {
        TODO("Not yet implemented")
    }

    override fun append(target: String, input: String) {
        TODO("Not yet implemented")
    }

    override fun remove(target: String, input: String) {
        TODO("Not yet implemented")
    }

}