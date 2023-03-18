package keb.services

interface TextEditorService {
    fun write(input: String)
    fun read(): String
}

class TextEditorServiceImpl : TextEditorService {
    override fun write(input: String) {
        TODO("Not yet implemented")
    }

    override fun read(): String {
        TODO("Not yet implemented")
    }
}