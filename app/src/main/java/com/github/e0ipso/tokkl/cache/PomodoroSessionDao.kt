package com.github.e0ipso.tokkl.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** The running Pomodoro row. [PomodoroSessionStore][com.github.e0ipso.tokkl.pomodoro.PomodoroSessionStore] writes it inside the queue's transaction. */
@Dao
interface PomodoroSessionDao {

    @Query("SELECT * FROM pomodoro_session WHERE slot = 0")
    fun current(): PomodoroSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(session: PomodoroSession)

    @Query("DELETE FROM pomodoro_session")
    fun delete()
}
