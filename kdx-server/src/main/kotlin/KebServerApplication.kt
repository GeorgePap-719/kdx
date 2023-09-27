package kdx.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KebServerApplication

fun main(args: Array<String>) {
    runApplication<KebServerApplication>(*args)
}