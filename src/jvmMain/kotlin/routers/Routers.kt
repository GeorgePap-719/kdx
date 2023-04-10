package keb.server.routers

import keb.server.services.DocumentService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.*

@Configuration
class Routers(private val documentHandler: DocumentHandler) {
    private val documentApiPrefix = API_PREFIX + "document/"

    @Bean
    fun documentRouter() = coRouter {
        accept(MediaType.APPLICATION_JSON).nest {
            GET(documentApiPrefix + DocumentNamePathVariable, documentHandler::read)
        }

    }
}

const val API_PREFIX = "/api/"

@Component
class DocumentHandler(private val documentService: DocumentService) {
    suspend fun read(request: ServerRequest): ServerResponse {
        val documentName = request.pathVariable(DocumentNamePathVariable)
        val document = documentService.read(documentName)
        return if (document == null) {
            ServerResponse.notFound().buildAndAwait()
        } else {
            ServerResponse.ok().bodyValueAndAwait(document)
        }
    }
}

const val DocumentNamePathVariable = "{name}"

//@Serializable
//data class ErrorInfo(val value: String)