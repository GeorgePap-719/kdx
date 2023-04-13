package keb.server.routers

import keb.server.services.DocumentService
import keb.server.utils.logger
import kotlinx.serialization.Serializable
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.*

@Configuration
class Routers(private val documentHandler: DocumentHandler) {
    private val documentApiPrefix = API_PREFIX + "document"

    @Bean
    fun documentRouter() = coRouter {
        accept(MediaType.APPLICATION_JSON).nest {
            GET("$documentApiPrefix/{$DocumentNamePathVariable}", documentHandler::read)
        }

    }
}

const val API_PREFIX = "/api/"

@Component
class DocumentHandler(private val documentService: DocumentService) {
    private val logger = logger()

    suspend fun read(request: ServerRequest): ServerResponse {
        val documentName = request.pathVariable(DocumentNamePathVariable)
        val document = documentService.read(documentName)
        logger.info("read document: $document")
        return if (document == null) {
            ServerResponse.notFound().buildAndAwait()
        } else {
            ServerResponse.ok().bodyValueAndAwait(document)
        }
    }
}

const val DocumentNamePathVariable = "name"

/**
 * A helper class to represent error responses in Json format.
 */
@Serializable
data class ErrorInfo(val error: String)