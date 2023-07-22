package keb.server.entities

import kotlinx.serialization.Serializable
import org.springframework.core.convert.converter.Converter
import org.springframework.data.annotation.Id
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.stereotype.Component

@Table("documentFile")
data class DocumentFileEntity(
    @Id
    @Column("id")
    val id: Long,

    @Column("document_id")
    val document: DocumentEntity,

    @Column("fileAddress")
    val fileAddress: FileAddress
)

@Serializable
data class FileAddress(val value: String)


@WritingConverter
@Component
class FileAddressToStringConverter : Converter<FileAddress, String> {
    override fun convert(source: FileAddress): String = source.value
}

@ReadingConverter
@Component
class StringToFileAddressConverter : Converter<String, FileAddress> {
    override fun convert(source: String): FileAddress = FileAddress(source)
}