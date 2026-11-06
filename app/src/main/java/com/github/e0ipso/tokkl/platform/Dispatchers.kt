package com.github.e0ipso.tokkl.platform

import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named

/** [Dispatchers.IO], for disk and network work. */
val IoDispatcher: Qualifier = named("IoDispatcher")

/** [Dispatchers.Default], which the timer event stream's clock runs on. */
val DefaultDispatcher: Qualifier = named("DefaultDispatcher")
