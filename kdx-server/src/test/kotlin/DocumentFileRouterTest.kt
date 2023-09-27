package keb.server

import dto.CreateDocument
import dto.CreateDocumentFile
import dto.DocumentFile
import entities.FileAddress
import kdx.server.dto.Text
import kdx.server.repositories.DocumentFileRepository
import keb.ropes.RopeLeaf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
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
class DocumentFileRouterTest(
    @Autowired
    private val webClient: WebClient,
    @Autowired
    private val documentRepository: DocumentFileRepository,
    @LocalServerPort
    private val port: Int
) {
    private val documentFileUrl = defaultUrl(port) + "/documentFile"

    @AfterEach
    fun afterEach(): Unit = runBlocking {
        documentRepository.deleteAll()
    }

    @Test
    fun shouldCreateDocumentFile(): Unit = runBlocking {
        val newDocumentFile = CreateDocumentFile(
            CreateDocument(Text(listOf(RopeLeaf("hi server", 1)))),
            FileAddress("~/helloWorld.txt")
        )
        val response = webClient
            .post()
            .uri("$documentFileUrl/create")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
//            .body(newDocumentFile)
            .bodyValue("newDocumentFile")

        response.awaitExchange {
            println(it.statusCode().value())
            val documentFile = it.awaitBody<DocumentFile>()
            println(documentFile.toString())
        }
    }
}

fun defaultUrl(port: Int): String = "http://localhost:$port/api/"