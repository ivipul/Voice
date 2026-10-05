package voice.core.data.repo.internals.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import voice.core.data.StoredCoPilotMessage

@Dao
public abstract class CoPilotMessageDao {

  @Query("SELECT * FROM coPilotMessage ORDER BY orderIndex")
  public abstract suspend fun all(): List<StoredCoPilotMessage>

  @Upsert
  public abstract suspend fun upsert(message: StoredCoPilotMessage)

  @Query("DELETE FROM coPilotMessage WHERE id = :id")
  public abstract suspend fun delete(id: String)
}
