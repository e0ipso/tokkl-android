package com.github.e0ipso.tokkl.navhost

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionSavedStateTest {
    @Test
    fun sessionDraftRestoresInANewProcessAndSignOutForgetsIt() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = Owner()
            val stores = NavigationStores()
            stores.after(0)
            draft(stores, activity).saved["description"] = "Unfinished work"
            val restoredActivity = Owner(activity.save())
            val restoredStores = NavigationStores()
            restoredStores.after(0)
            assertEquals("Unfinished work", draft(restoredStores, restoredActivity).saved.get<String>("description"))
            restoredStores.after(1)
            assertNull(draft(restoredStores, restoredActivity).saved.get<String>("description"))
            stores.entries.clearAllKeys()
            restoredStores.entries.clearAllKeys()
        }
    }

    @Test
    fun restoringSignInDiscardsTheEarlierAccountsDraft() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = Owner()
            val stores = NavigationStores()
            draft(stores, activity).saved["description"] = "Earlier account"
            val restoredActivity = Owner(activity.save())
            val restoredStores = NavigationStores()
            restoredStores.discardRestoredSession(restoredActivity)
            assertNull(draft(restoredStores, restoredActivity).saved.get<String>("description"))
            stores.entries.clearAllKeys()
            restoredStores.entries.clearAllKeys()
        }
    }

    private fun draft(stores: NavigationStores, activity: Owner): Draft = ViewModelProvider(
        stores.session(activity),
        viewModelFactory { initializer { Draft(createSavedStateHandle()) } },
    )[Draft::class.java]

    private class Draft(val saved: SavedStateHandle) : ViewModel()

    private class Owner(restored: Bundle? = null) :
        SavedStateRegistryOwner,
        ViewModelStoreOwner {
        override val lifecycle = LifecycleRegistry(this)
        override val viewModelStore = ViewModelStore()
        private val controller = SavedStateRegistryController.create(this)
        override val savedStateRegistry get() = controller.savedStateRegistry

        init {
            controller.performAttach()
            controller.performRestore(restored)
            enableSavedStateHandles()
            lifecycle.currentState = Lifecycle.State.CREATED
        }

        fun save() = Bundle().also(controller::performSave)
    }
}
