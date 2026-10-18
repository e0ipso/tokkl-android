package com.github.e0ipso.tokkl.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.Dispatchers

/** Keeps the reports screen's state across configuration changes, so turning the phone sends no request. */
class ReportsViewModel(application: Application) : AndroidViewModel(application) {

    val model = ReportsModel(
        accountSettings = (application as TokklApplication).accountSettings.state,
        client = Session(application)::client,
        scope = viewModelScope,
        io = Dispatchers.IO,
    )
}
