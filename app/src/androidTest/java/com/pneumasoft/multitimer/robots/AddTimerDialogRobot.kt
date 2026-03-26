// ✅ FULL FILE VERSION
// Path: C:/local/Android/Timers/app/src/androidTest/java/com/pneumasoft/multitimer/robots/AddTimerDialogRobot.kt

package com.pneumasoft.multitimer.robots

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.pneumasoft.multitimer.R
import org.hamcrest.Matchers.allOf

class AddTimerDialogRobot : BaseRobot() {

    fun enterName(name: String) {
        typeText(R.id.timer_name_edit, name)
    }

    fun setHours(hours: Int) {
        // 🔄 MODIFIED: Ahora usamos botones Up
        repeat(hours) {
            onView(withId(R.id.hours_up_button)).perform(click())
        }
    }

    fun setMinutes(minutes: Int) {
        // ❌ REMOVED: El 'minutes_slider' ya no existe
        // 🔄 MODIFIED: Ahora usamos botones Up para minutos
        repeat(minutes) {
            onView(withId(R.id.minutes_up_button)).perform(click())
        }
    }

    fun setSeconds(seconds: Int) {
        // ✅ NEW: Añadido para completar la lógica de los botones actuales
        repeat(seconds) {
            onView(withId(R.id.seconds_up_button)).perform(click())
        }
    }

    fun tapAdd() {
        // En los diálogos de Android, el botón positivo a veces no tiene ID, se busca por texto
        onView(withText("Add")).perform(click())
    }

    fun shouldStillBeOpen() {
        onView(withId(R.id.timer_name_edit)).check(matches(isDisplayed()))
    }

    // ❌ REMOVED: setProgress ya no es necesario porque no hay SeekBar

    operator fun invoke(block: AddTimerDialogRobot.() -> Unit) {
        this.apply(block)
    }
}