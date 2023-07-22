package keb.server.entities

import io.r2dbc.spi.Row
import keb.server.dto.Text
import keb.server.serialization.Json
import keb.server.util.append
import keb.server.util.getColumn
import kotlinx.serialization.encodeToString
import org.springframework.core.convert.converter.Converter
import org.springframework.data.annotation.Id
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.r2dbc.mapping.OutboundRow
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.stereotype.Component

@Table("document")
data class DocumentEntity(
    @Id
    @Column("id")
    val id: Long,

    @Column("text")
    val text: Text
)

@WritingConverter
@Component
class DocumentEntityToRowConverter : Converter<DocumentEntity, OutboundRow> {
    override fun convert(source: DocumentEntity): OutboundRow = OutboundRow().apply {
        append("id", source.id)
        val serializedText = Json.encodeToString(source.text)
        append("text", serializedText)
    }
}

@ReadingConverter
@Component
class RowToDocumentEntityConverter : Converter<Row, DocumentEntity> {
    override fun convert(source: Row): DocumentEntity? {
        val text = Json.decodeFromString<Text>(source.getColumn<String>("text"))
        return DocumentEntity(source.getColumn("id"), text)
    }
}