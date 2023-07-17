package keb.server.routers

import keb.server.model.Text
import keb.server.services.DocumentService
import keb.server.util.logger
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
            POST("$documentApiPrefix/{$DocumentNamePathVariable}/append", documentHandler::append)
            POST("$documentApiPrefix/{$DocumentNamePathVariable}/remove", documentHandler::remove)
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

    suspend fun append(request: ServerRequest): ServerResponse {
        val documentName = request.pathVariable(DocumentNamePathVariable)
        val body = request.awaitAndRequireBody<Text>()
        logger.info("preparing to update document: $documentName")
        val result = documentService.append(documentName, body)
        return if (result > 0) {
            ServerResponse.ok().buildAndAwait()
        } else {
            ServerResponse.notFound().buildAndAwait()
        }
    }

    suspend fun remove(request: ServerRequest): ServerResponse {
        val documentName = request.pathVariable(DocumentNamePathVariable)
        val body = request.awaitAndRequireBody<Text>()
        logger.info("preparing to update document: $documentName")
        val result = documentService.removeText(documentName, body)
        return if (result > 0) {
            ServerResponse.ok().buildAndAwait()
        } else {
            ServerResponse.notFound().buildAndAwait()
        }
    }
}

suspend inline fun <reified T : Any> ServerRequest.awaitAndRequireBody(): T {
    val body = awaitBodyOrNull<T>()
    requireNotNull(body) { "body is expected to by type of ${T::class.simpleName}" }
    return body
}

const val DocumentNamePathVariable = "name"

/**
 * A helper class to represent message in Json format for error responses.
 */
@Serializable
data class ErrorInfo(val error: String)


/**
 * A helper class to represent message in Json format for success responses.
 */
@Serializable
data class SuccessInfo(val message: String)