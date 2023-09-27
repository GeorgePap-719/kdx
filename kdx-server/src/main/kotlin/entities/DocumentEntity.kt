package entities

import io.r2dbc.spi.Row
import kdx.server.dto.Text
import kdx.server.serialization.Json
import kdx.server.util.append
import kdx.server.util.getColumn
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

    @Column("text_json")
    val text: Text
)

@WritingConverter
@Component
object DocumentEntityToRowConverter : Converter<DocumentEntity, OutboundRow> {
    override fun convert(source: DocumentEntity): OutboundRow = OutboundRow().apply {
        append("id", source.id)
        val serializedText = Json.encodeToString(source.text)
        append("text_json", serializedText)
    }
}

@ReadingConverter
@Component
object RowToDocumentEntityConverter : Converter<Row, DocumentEntity> {
    override fun convert(source: Row): DocumentEntity {
        val text = Json.decodeFromString<Text>(source.getColumn<String>("text_json"))
        return DocumentEntity(source.getColumn("id"), text)
    }
}