package keb.server.entities

import keb.server.util.append
import kotlinx.serialization.Serializable
import org.springframework.core.convert.converter.Converter
import org.springframework.data.annotation.Id
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.r2dbc.mapping.OutboundRow
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.stereotype.Component

@Table("document_file")
data class DocumentFileEntity(
    @Id
    @Column("id")
    val id: Long,

    val document: DocumentEntity,

    @Column("file_address")
    val fileAddress: FileAddress
)

@Serializable
data class FileAddress(val value: String)


@WritingConverter
@Component
class DocumentFileToRowConverter : Converter<DocumentFileEntity, OutboundRow> {
    override fun convert(source: DocumentFileEntity): OutboundRow? {
        return OutboundRow().apply {
            append("id", source.id)
            append("document_id", source.document.id)
            append("file_address", source.fileAddress)
        }
    }
}

/*
 * No @ReadingConverter for DocumentFileEntity,
 * since we need to write sql-queries manually
 * to handle foreign-key on DocumentEntity.
 */

@WritingConverter
@Component
object FileAddressToStringConverter : Converter<FileAddress, String> {
    override fun convert(source: FileAddress): String = source.value
}

@ReadingConverter
@Component
object StringToFileAddressConverter : Converter<String, FileAddress> {
    override fun convert(source: String): FileAddress = FileAddress(source)
}