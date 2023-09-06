package keb.server.routers.request

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyToMono
import kotlin.reflect.KType
import kotlin.reflect.full.starProjectedType

suspend inline fun <reified T : Any> ServerRequest.awaitAndReceive(): T {
    val body = try {
        awaitBody<T>()
    } catch (e: Throwable) {
        // We catch only exceptions related to `coroutines` bridge await.single()
        // and to serialization exceptions.
        if (e is IllegalArgumentException || e is NoSuchElementException) {
            throw IllegalArgumentException("Body is expected to be type of ${T::class.simpleName}")
        }
        // Anything else we propagate it top top-level handler.
        throw e
    }
    return body
}

fun ServerRequest.pathVariableOrNull(name: String): String? {
    val vars = pathVariables()
    return vars[name]
}

// ---- experimental -----

/**
 * Receives the incoming content for this [request][ServerRequest] and transforms it to the requested `T` type.
 *
 * @throws IllegalArgumentException when content cannot be transformed to the requested type.
 */
suspend inline fun <reified T : Any> ServerRequest.awaitReceive(): T = awaitReceiveNullable<T>()
    ?: throw ContentTransformationException(starProjectedType<T>())

/**
 * Receives the incoming content for this [request][ServerRequest] and transforms it to the requested `T` type.
 *
 * @throws IllegalArgumentException when content cannot be transformed to the requested type.
 */
suspend inline fun <reified T : Any> ServerRequest.awaitReceiveNullable(): T? {
    try {
        return bodyToMono<T>().awaitSingleOrNull()
    } catch (e: IllegalArgumentException) {
        throw ContentTransformationException(starProjectedType<T>())
    }
}

@PublishedApi
internal class ContentTransformationException(
    type: KType
) : IllegalArgumentException("Cannot transform this request's content to $type")

@PublishedApi
internal inline fun <reified T : Any> starProjectedType(): KType {
    return T::class.starProjectedType
}