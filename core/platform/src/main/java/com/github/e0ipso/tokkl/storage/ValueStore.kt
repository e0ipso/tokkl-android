package com.github.e0ipso.tokkl.storage

import java.io.IOException

/** One value the phone keeps on disk. Each call switches to the store's I/O dispatcher, so call it from any thread. */
interface ValueStore<T> {

    suspend fun load(): T?

    @Throws(IOException::class)
    suspend fun save(value: T)
}

/** A [ValueStore] whose value sign-out deletes. */
interface ClearableStore<T> : ValueStore<T> {

    @Throws(IOException::class)
    suspend fun clear()
}
