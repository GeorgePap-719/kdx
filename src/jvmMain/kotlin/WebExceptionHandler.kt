package keb.server

import keb.server.utils.logger
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono

/*
 The DefaultErrorWebExceptionHandler provided by Spring Boot for error handling is ordered at -1.
 The ResponseStatusExceptionHandler provided by Spring Framework is ordered at 0.
 So we add @Order(-2) on this exception handling component, to order it before the existing ones.
 */
@Component
@Order(-2)
class WebAppExceptionHandler : WebExceptionHandler {
    private val logger = logger()

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> = mono {
        val response = when (ex) {
            is IllegalArgumentException -> {
                exchange.response.let {
                    it.statusCode = HttpStatus.BAD_REQUEST
                    it.headers.contentType = MediaType.APPLICATION_JSON
                    if (ex.message != null) {
                        val response = it.bufferFactory().wrap("""{"error":"${ex.message}"}""".encodeToByteArray())
                        it.writeWith(Mono.just(response))
                    } else {
                        Mono.empty()
                    }
                }.also { logger.warn(ex.stackTraceToString()) }
            }

            is IllegalStateException -> {
                exchange.response.let {
                    it.statusCode = HttpStatus.INTERNAL_SERVER_ERROR
                    it.headers.contentType = MediaType.APPLICATION_JSON
                    logger.error(ex.stackTraceToString())
                    Mono.empty() // return plain 500 status. We cannot reveal error info at this stage.
                }
            }

            else -> Mono.error(ex) // default spring error response
        }
        response.awaitSingleOrNull()
    }
}