package com.github.e0ipso.tokkl.storage

/** A [ValueStore] in memory. [onSave] sees each value first, and can throw to fail the write. */
class InMemoryValueStore<T>(private var value: T?, private val onSave: (T) -> Unit = {}) : ValueStore<T> {

    override fun load(): T? = value

    override fun save(value: T) {
        onSave(value)
        this.value = value
    }
}
