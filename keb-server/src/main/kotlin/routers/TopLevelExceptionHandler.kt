package keb.server.routers

import keb.server.util.debug
import keb.server.util.error
import keb.server.util.logger
import kotlinx.coroutines.reactor.mono
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono

/*
 * The DefaultErrorWebExceptionHandler provided by Spring Boot for error handling is ordered at -1.
 * The ResponseStatusExceptionHandler provided by Spring Framework is ordered at 0.
 * So we add @Order(-2) on this exception handling component, to order it before the existing ones.
 */
@Order(-2)
@Component
class TopLevelExceptionHandler : WebExceptionHandler {
    private val logger = logger()

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> = mono {
        when (ex) {
            is IllegalArgumentException -> {
                logger.debug { ex.stackTraceToString() }
                exchange.response.let {
                    it.statusCode = HttpStatus.BAD_REQUEST
                    it.headers.contentType = MediaType.APPLICATION_JSON
                    val exMessage = ex.message
                    if (exMessage == null) {
                        it.writeWith { sub -> sub.onComplete() }
                    } else {
                        val response = it.bufferFactory().wrap(errorMessage(exMessage))
                        it.writeWith { sub -> sub.onNext(response) }
                    }
                }
            }

            else -> {
                exchange.response.let {
                    logger.error { ex.stackTraceToString() }
                    it.statusCode = HttpStatus.INTERNAL_SERVER_ERROR
                    it.headers.contentType = MediaType.APPLICATION_JSON
                    val response = it.bufferFactory().wrap(internalServerError)
                    it.writeWith { sub -> sub.onNext(response) }
                }
            }
        }
    }.then()
}

private fun errorMessage(error: String): ByteArray = ErrorMessage(error).encodeToJson().toByteArray()

private val internalServerError: ByteArray = ErrorMessage("internal server-error").encodeToJson().toByteArray()

/**
 * A helper class to represent error-messages in Json format for error responses.
 */
@Serializable
data class ErrorMessage(val error: String)

fun ErrorMessage.encodeToJson(): String = Json.encodeToString(this)