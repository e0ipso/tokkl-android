package com.github.e0ipso.tokkl.storage

/** A [ClearableStore] in memory. [onSave] sees each value first, and can throw to fail the write. */
class InMemoryValueStore<T>(private var value: T?, private val onSave: (T) -> Unit = {}) : ClearableStore<T> {

    override fun load(): T? = value

    override fun save(value: T) {
        onSave(value)
        this.value = value
    }

    override fun clear() {
        value = null
    }
}

/** A [ValueStore] that reads with [load] and writes with [save], for state a test keeps itself. */
fun <T> valueStore(load: () -> T?, save: (T) -> Unit): ValueStore<T> = object : ValueStore<T> {
    override fun load(): T? = load()

    override fun save(value: T) = save(value)
}
