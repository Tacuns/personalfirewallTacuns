package com.sentinel.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AppControlViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppControlViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // The ViewModel outlives the Activity (rotation, language change), so it must not
            // keep the Activity itself. This context comes from the application but carries the
            // Activity's configuration, so strings stay in the language chosen in the app on
            // every Android version (AppCompat applies that choice to the Activity only below 13).
            return AppControlViewModel(
                context.applicationContext.createConfigurationContext(context.resources.configuration)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
