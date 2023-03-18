package keb.server.configuration

import io.r2dbc.spi.ConnectionFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.r2dbc.ConnectionFactoryBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement

@Configuration
@EnableTransactionManagement
class R2dbcConfiguration(
    private val converters: List<Converter<*, *>>,
    @Value("\${spring.r2dbc.username}") private val username: String,
    @Value("\${spring.r2dbc.password}") private val password: String,
    @Value("\${spring.r2dbc.url}") private val url: String,
    @Value("\${spring.r2dbc.database}") private val database: String
) : AbstractR2dbcConfiguration() {

    @Bean
    fun transactionManager(connectionFactory: ConnectionFactory): ReactiveTransactionManager =
        R2dbcTransactionManager(connectionFactory)

    @Bean
    override fun connectionFactory(): ConnectionFactory = ConnectionFactoryBuilder
        .withUrl(url)
        .username(username)
        .password(password)
//        .database(database)
        .build()

    override fun getCustomConverters(): MutableList<Any> = converters.toMutableList()
}