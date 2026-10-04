package voice.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One co-pilot Feed message as saved on disk. [orderIndex] keeps the Feed in the order things happened. */
@Entity(tableName = "coPilotMessage")
public data class StoredCoPilotMessage(
  @PrimaryKey
  val id: String,
  val bookId: BookId,
  val role: String,
  val text: String,
  val timestampMs: Long,
  val isVisualPriority: Boolean,
  val imagePath: String?,
  val snipChapterId: ChapterId?,
  val snipPositionInChapterMs: Long?,
  val orderIndex: Long,
)
