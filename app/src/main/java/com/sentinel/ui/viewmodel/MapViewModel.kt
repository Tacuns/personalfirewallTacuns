package com.sentinel.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.core.geo.CountryLookup
import com.sentinel.core.geo.DomainResolver
import com.sentinel.core.logs.LogDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import com.sentinel.core.utils.AppVisibility
import kotlinx.coroutines.launch
import java.util.Calendar

data class ResolvedDomain(
    val domain:      String,
    val ip:          String?,   // null = resolution pending or failed
    val countryCode: String?,   // ISO 3166-1 alpha-2, e.g. "US"; null = pending / unknown
    val count:       Int,
    val type:        String     // "BLOCKED" or "ALLOWED"
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val logDao = LogDatabase.getInstance(application).packetLogDao()

    private val _domains = MutableStateFlow<List<ResolvedDomain>>(emptyList())
    val domains: StateFlow<List<ResolvedDomain>> = _domains.asStateFlow()

    init {
        CountryLookup.init(application)
        refresh()
        viewModelScope.launch {
            @Suppress("OPT_IN_USAGE")
            logDao.getRecentFlow(1)
                .debounce(3_000)
                .collect { if (AppVisibility.isVisible) refresh() }
        }
        // Skipped refreshes while closed are caught up once, when the app is opened again.
        viewModelScope.launch {
            AppVisibility.visible.drop(1).collect { if (it) refresh() }
        }
    }

    private fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val since   = startOfTodayMs()
            val blocked = logDao.topDestinations("BLOCKED", since, 6)
            val allowed = logDao.topDestinations("ALLOWED", since, 20)

            // Build initial list with whatever is already cached (instant, no network)
            val initial = (blocked.map { it to "BLOCKED" } + allowed.map { it to "ALLOWED" })
                .map { (row, type) ->
                    val cachedIp = if (type == "ALLOWED") DomainResolver.cached(row.name) else null
                    val cachedCountry = cachedIp?.let { CountryLookup.lookup(it) }
                    ResolvedDomain(row.name, cachedIp, cachedCountry, row.count, type)
                }

            _domains.value = initial

            // Country only for ALLOWED websites. A blocked website was stopped before any
            // server was reached, so it has no real location — and looking it up here would
            // send the very name the user blocked out to the network. awaitReady() ensures
            // the country file is ready before lookup (no-op after first load).
            initial.filter { it.type == "ALLOWED" }.forEach { entry ->
                launch resolver@{
                    val ip = entry.ip ?: DomainResolver.resolve(entry.domain) ?: return@resolver
                    CountryLookup.awaitReady()
                    val country = CountryLookup.lookup(ip)
                    _domains.value = _domains.value.map { d ->
                        if (d.domain == entry.domain) d.copy(ip = ip, countryCode = country) else d
                    }
                }
            }
        }
    }

    private fun startOfTodayMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
