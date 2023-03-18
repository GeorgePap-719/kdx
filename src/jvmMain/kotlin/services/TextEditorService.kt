package keb.server.services

import org.springframework.stereotype.Service

interface TextEditorService {
    fun write(input: String)
    fun read(): String
}

@Service
class TextEditorServiceImpl : TextEditorService {
    override fun write(input: String) {
        TODO("Not yet implemented")
    }

    override fun read(): String {
        TODO("Not yet implemented")
    }
}