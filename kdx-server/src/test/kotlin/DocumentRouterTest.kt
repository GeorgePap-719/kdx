package kdx.server
//
//import dto.Document
//import kdx.server.dto.Text
//import entities.DocumentEntity
//import keb.server.entities.toDocument
//import kdx.server.repositories.DocumentRepository
//import keb.server.routers.ErrorInfo
//import kotlinx.coroutines.runBlocking
//import org.junit.jupiter.api.AfterEach
//import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Test
//import org.junit.jupiter.api.TestInstance
//import org.springframework.beans.factory.annotation.Autowired
//import org.springframework.boot.test.context.SpringBootTest
//import org.springframework.boot.test.web.server.LocalServerPort
//import org.springframework.http.MediaType
//import org.springframework.web.reactive.function.client.*
//import kotlin.test.assertEquals
//
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//class DocumentRouterTest(
//    @Autowired
//    private val webClient: WebClient,
//    @Autowired
//    private val documentRepository: DocumentRepository,
//    @LocalServerPort
//    private val port: Int
//) {
//    private val testDocument = DocumentEntity("test", Text("hi world"))
//    private val documentUrl = defaultUrl(port) + "/document"
//
//    @BeforeEach
//    fun setUp(): Unit = runBlocking {
//        documentRepository.save(testDocument)
//    }
//
//    @AfterEach
//    fun afterEach(): Unit = runBlocking {
//        documentRepository.deleteAll()
//    }
//
//    @Test
//    fun shouldReadExistingDocument(): Unit = runBlocking {
//        val response = webClient
//            .get()
//            .uri("$documentUrl/${testDocument.name}")
//            .accept(MediaType.APPLICATION_JSON)
//            .awaitExchange {
//                val statusCode = it.statusCode().value()
//                if (statusCode != 200) {
//                    val error = it.awaitBody<ErrorInfo>()
//                    throw AssertionError("failed with status code:$statusCode and message:$error")
//                }
//                it.awaitBody<Document>()
//            }
//        println(response)
//        assertEquals(testDocument.toDocument(), response)
//    }
//
//    @Test
//    fun shouldAppendDocument(): Unit = runBlocking {
//        val text = Text(", i am an editor")
//        webClient
//            .post()
//            .uri("$documentUrl/${testDocument.name}/append")
//            .accept(MediaType.APPLICATION_JSON)
//            .bodyValue(text)
//            .awaitExchange {
//                val statusCode = it.statusCode().value()
//                if (statusCode != 200) {
//                    val error = it.awaitBody<ErrorInfo>()
//                    throw AssertionError("failed with status code:$statusCode and message:$error")
//                }
//            }
//        val result = documentRepository.findByName(testDocument.name)
//        val appendedText = testDocument.text + text
//        println(appendedText)
//        assert(result?.text == appendedText)
//    }
//
//    @Test
//    fun shouldRemoveTextFromDocument(): Unit = runBlocking {
//        val text = Text("world")
//        webClient
//            .post()
//            .uri("$documentUrl/${testDocument.name}/remove")
//            .accept(MediaType.APPLICATION_JSON)
//            .bodyValue(text)
//            .awaitExchange {
//                val statusCode = it.statusCode().value()
//                if (statusCode != 200) {
//                    val error = it.awaitBody<ErrorInfo>()
//                    throw AssertionError("failed with status code:$statusCode and message:$error")
//                }
//            }
//        val result = documentRepository.findByName(testDocument.name)
//        val updatedText = testDocument.text - text
//        println(updatedText)
//        assert(result?.text == updatedText)
//    }
//}
//
//fun defaultUrl(port: Int): String = "http://localhost:$port/api/"
