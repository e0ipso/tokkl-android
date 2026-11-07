package com.github.e0ipso.tokkl.entry

import com.github.e0ipso.tokkl.navigation.EntryFormRequest
import com.github.e0ipso.tokkl.navigation.EntryOpening

/** Opens [form] on what this request asks for. */
internal fun EntryFormRequest.openIn(form: EntryFormViewModel) {
    when (val opening = opening) {
        is EntryOpening.Edit -> form.edit(opening.entryId, older)

        is EntryOpening.Running -> form.editRunning(opening.identity)

        is EntryOpening.New -> {
            val day = opening.day
            if (opening.noTimes && day != null) form.newAllDay(day) else form.new(day)
        }
    }
}
