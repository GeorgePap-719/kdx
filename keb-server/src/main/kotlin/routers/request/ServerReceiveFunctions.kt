package keb.server.routers.request

import keb.server.serialization.Json
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.awaitBody

suspend inline fun <reified T : Any> ServerRequest.awaitAndReceive(): T {
    val body = try {
        awaitBody<T>()
    } catch (e: Throwable) {
        // We catch only exceptions related to `coroutines` bridge await.single()
        // and to serialization exceptions.
        if (e is IllegalArgumentException || e is NoSuchElementException) {
            throw IllegalArgumentException(bodyTypeErrorMessage<T>())
        }
        // Anything else we propagate it top top-level handler.
        throw e
    }
    return body
}

// private API
inline fun <reified T : Any> deserializeBody(body: String): T {
    try {
        return Json.decodeFromString<T>(body)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException(bodyTypeErrorMessage<T>())
    }
}

// private API
inline fun <reified T : Any> bodyTypeErrorMessage(): String {
    return "Body is expected to be type of ${T::class.simpleName}"
}

fun ServerRequest.pathVariableOrNull(name: String): String? {
    val vars = pathVariables()
    return vars[name]
}