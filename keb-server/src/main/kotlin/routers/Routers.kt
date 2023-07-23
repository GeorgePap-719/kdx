package keb.server.routers

import keb.server.dto.CreateDocumentFile
import keb.server.routers.request.awaitAndReceive
import keb.server.services.DocumentFileService
import keb.server.util.info
import keb.server.util.logger
import kotlinx.serialization.Serializable
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.*

@Configuration
class Routers(private val documentFileHandler: DocumentFileHandler) {
    @Bean
    fun documentRouter() = coRouter {
        accept(MediaType.APPLICATION_JSON).nest {
            POST("$documentApiPrefix/create", documentFileHandler::create)
            GET("$documentApiPrefix/{$DocumentIdPathVariable}", documentFileHandler::readFromId)
//            POST("$documentApiPrefix/{$DocumentFileAddressPathVariable}/append", documentFileHandler::append)
//            POST("$documentApiPrefix/{$DocumentFileAddressPathVariable}/remove", documentFileHandler::remove)
        }
    }
}

const val API_PREFIX = "/api/"

private const val documentApiPrefix = API_PREFIX + "documentFile"

@Component
class DocumentFileHandler(private val documentFileService: DocumentFileService) {
    private val logger = logger()

    suspend fun create(request: ServerRequest): ServerResponse {
        logger.info { "request: $documentApiPrefix/create" }
        val awaitBody = request.awaitAndReceive<CreateDocumentFile>()
        logger.info { awaitBody.toString() }
//            //request.awaitBody<>() TODO: check spring's impl
//            request.awaitBody<CreateDocumentFile>()
//            val body = request.awaitAndRequireBody<CreateDocumentFile>()
//            val documentFile = documentFileService.create(body)
        return ServerResponse.ok().buildAndAwait()
    }

    suspend fun readFromId(request: ServerRequest): ServerResponse {
        logger.info { "request: $documentApiPrefix/{$DocumentIdPathVariable}" }
        //TODO handle pathVariable()
        val documentId = request.pathVariable(DocumentIdPathVariable).toLong()
        val documentFile = documentFileService.read(documentId)
        return if (documentFile == null) {
            ServerResponse.notFound().buildAndAwait()
        } else {
            ServerResponse.ok().bodyValueAndAwait(documentFile)
        }
    }

//    suspend fun read(request: ServerRequest): ServerResponse {
//        val documentName = request.pathVariable(DocumentFileAddressPathVariable)
//        val document = documentService.read(documentName)
//        logger.info("read document: $document")
//        return if (document == null) {
//            ServerResponse.notFound().buildAndAwait()
//        } else {
//            ServerResponse.ok().bodyValueAndAwait(document)
//        }
//    }
//
//    suspend fun append(request: ServerRequest): ServerResponse {
//        val documentName = request.pathVariable(DocumentFileAddressPathVariable)
//        val body = request.awaitAndRequireBody<Text>()
//        logger.info("preparing to update document: $documentName")
//        val result = documentService.append(documentName, body)
//        return if (result > 0) {
//            ServerResponse.ok().buildAndAwait()
//        } else {
//            ServerResponse.notFound().buildAndAwait()
//        }
//    }
//
//    suspend fun remove(request: ServerRequest): ServerResponse {
//        val documentName = request.pathVariable(DocumentFileAddressPathVariable)
//        val body = request.awaitAndRequireBody<Text>()
//        logger.info("preparing to update document: $documentName")
//        val result = documentService.removeText(documentName, body)
//        return if (result > 0) {
//            ServerResponse.ok().buildAndAwait()
//        } else {
//            ServerResponse.notFound().buildAndAwait()
//        }
//    }
}

const val DocumentFileAddressPathVariable = "file_address"
const val DocumentIdPathVariable = "id"


/**
 * A helper class to represent message in Json format for success responses.
 */
@Serializable
data class SuccessInfo(val message: String)