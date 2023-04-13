package keb.server

import keb.Document
import keb.Text
import keb.server.entities.DocumentEntity
import keb.server.repositories.DocumentRepository
import keb.server.routers.ErrorInfo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchange

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentRouterTest(
    @Autowired
    private val webClient: WebClient,
    @Autowired
    private val documentRepository: DocumentRepository,
    @LocalServerPort
    private val port: Int
) {
    private val testDocument = DocumentEntity("test", Text("hi world"))
    private val documentUrl = defaultUrl(port) + "/document"

    @BeforeEach
    fun setUp(): Unit = runBlocking {
        documentRepository.deleteAll() // run fresh
    }

    @Test
    fun shouldReadExistingDocument(): Unit = runBlocking {
        documentRepository.save(testDocument)

        val response = webClient
            .get()
            .uri("$documentUrl/${testDocument.name}")
            .accept(MediaType.APPLICATION_JSON)
            .awaitExchange {
                val statusCode = it.statusCode().value()
                if (statusCode != 200) {
                    println(statusCode)
                    println(it.awaitBody<ErrorInfo>())
                    throw AssertionError("failed with status code:$statusCode")
                }
                it.awaitBody<Document>()
            }
        println(response)
    }
}

fun defaultUrl(port: Int): String = "http://localhost:$port/api/"
