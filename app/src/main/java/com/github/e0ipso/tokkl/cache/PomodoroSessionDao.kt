package com.github.e0ipso.tokkl.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** The running Pomodoro row. [PomodoroSessionStore][com.github.e0ipso.tokkl.pomodoro.PomodoroSessionStore] writes it inside the queue's transaction. */
@Dao
interface PomodoroSessionDao {

    @Query("SELECT * FROM pomodoro_session WHERE slot = 0")
    fun current(): PomodoroSession?

    /** Navigation follows the durable session, including changes made outside its page. */
    @Query("SELECT EXISTS(SELECT 1 FROM pomodoro_session WHERE slot = 0)")
    fun running(): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(session: PomodoroSession)

    @Query("DELETE FROM pomodoro_session")
    fun delete()
}
