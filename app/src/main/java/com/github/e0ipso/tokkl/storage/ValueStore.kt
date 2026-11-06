package com.github.e0ipso.tokkl.storage

import java.io.IOException

/** One value the phone keeps on disk. Each call reads or writes the disk, so call it off the main thread. */
interface ValueStore<T> {

    /** The stored value, or null when none is stored. */
    fun load(): T?

    @Throws(IOException::class)
    fun save(value: T)
}
