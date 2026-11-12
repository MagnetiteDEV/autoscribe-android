package com.autoscribe.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoscribe.android.data.AppSettings
import com.autoscribe.android.data.EMBEDDED_POPS
import com.autoscribe.android.data.ServerListImport
import com.autoscribe.android.data.Strings
import com.autoscribe.android.engine.CsvExport
import com.autoscribe.android.engine.Pop
import com.autoscribe.android.engine.TestPlan
import com.autoscribe.android.engine.TestResult
import com.autoscribe.android.engine.TestTask
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * All UI state and actions. The engine itself lives in the foreground
 * service; this ViewModel prepares runs, owns settings/results/profiles,
 * and translates service state into toasts.
 */
class MainViewModel : ViewModel() {

    sealed interface StartCheck {
        data object Ready : StartCheck
        data class NeedFreshConfirm(val stored: Int) : StartCheck
        data class Blocked(val messageKey: String) : StartCheck
    }

    private val _language = MutableStateFlow("en")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _pops = MutableStateFlow<List<Pop>>(EMBEDDED_POPS)
    val pops: StateFlow<List<Pop>> = _pops.asStateFlow()

    private val _importedListAt = MutableStateFlow(0L)
    val importedListAt: StateFlow<Long> = _importedListAt.asStateFlow()

    private val _imported = MutableStateFlow<Set<String>>(emptySet())
    val imported: StateFlow<Set<String>> = _imported.asStateFlow()

    private val _results = MutableStateFlow<List<TestResult>>(emptyList())
    val results: StateFlow<List<TestResult>> = _results.asStateFlow()

    private val _wgInstalled = MutableStateFlow(false)
    val wgInstalled: StateFlow<Boolean> = _wgInstalled.asStateFlow()

    private val _wgPermission = MutableStateFlow(false)
    val wgPermission: StateFlow<Boolean> = _wgPermission.asStateFlow()

    private val _profileNames = MutableStateFlow<List<String>>(emptyList())
    val profileNames: StateFlow<List<String>> = _profileNames.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    /** Strings for the current language; recomputed on language change. */
    fun strings(): Strings = Strings(_language.value)

    init {
        val store = AppDeps.settings
        _settings.value = store.loadSettings()
        _imported.value = store.loadImported()
        _profileNames.value = store.profileNames()
        _importedListAt.value = store.importedListTimestamp
        ServerListImport.loadImported(AppDeps.context)?.let { _pops.value = it }
        val savedLang = store.language
        _language.value = savedLang.ifEmpty {
            when (Locale.getDefault().language) {
                "fa" -> Strings.LANG_FA
                "ru" -> Strings.LANG_RU
                else -> Strings.LANG_EN
            }
        }
        refreshWgStatus()
        refreshResults()
        viewModelScope.launch {
            EngineState.resultsVersion.collect { refreshResults() }
        }
        viewModelScope.launch {
            var lastOutcomeId = -1L
            EngineState.lastOutcome.collect { outcome ->
                if (outcome != null && outcome.id != lastOutcomeId) {
                    lastOutcomeId = outcome.id
                    val s = strings()
                    when {
                        outcome.keptConnection != null -> toast(
                            s.t("connected_to", "x" to outcome.keptConnection),
                        )
                        !outcome.stopped -> toast(
                            s.t(
                                "finished_toast",
                                "ok" to s.num(outcome.ok),
                                "fail" to s.num(outcome.fail),
                            ),
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            var lastProbeId = -1L
            EngineState.probeResult.collect { probe ->
                if (probe != null && probe.id != lastProbeId) {
                    lastProbeId = probe.id
                    val s = strings()
                    val label = "${probe.task.pop.displayName} · ${probe.task.route}"
                    toast(
                        if (probe.connected) {
                            s.t("tunnel_probe_ok", "x" to label)
                        } else {
                            s.t("tunnel_probe_fail", "x" to label)
                        },
                    )
                }
            }
        }
    }

    private fun toast(message: String) {
        _toasts.tryEmit(message)
    }

    fun refreshWgStatus() {
        _wgInstalled.value = AppDeps.wireGuard.isInstalled()
        _wgPermission.value = AppDeps.wireGuard.hasControlPermission()
    }

    fun refreshResults() {
        viewModelScope.launch(Dispatchers.IO) {
            val rows = AppDeps.db.fetchAll()
            _results.value = rows
        }
    }

    // -- Settings --

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        AppDeps.settings.saveSettings(next)
    }

    fun setLanguage(lang: String) {
        if (EngineState.running.value) {
            toast(strings().t("lang_busy"))
            return
        }
        if (lang !in Strings.LANGUAGES) return
        _language.value = lang
        AppDeps.settings.language = lang
    }

    // -- Validation (desktop read_number / bad_host rules) --

    /** Returns a translated error message, or null when the values are valid. */
    fun validateTiming(timeout: String, settle: String, pingCount: String, host: String): String? {
        fun number(value: String, nameKey: String, min: Int, max: Int): String? {
            val n = value.trim().toIntOrNull()
                ?: return strings().t(
                    "must_be_number",
                    "name" to strings().t(nameKey), "a" to min, "b" to max,
                )
            if (n < min || n > max) {
                return strings().t(
                    "must_be_number",
                    "name" to strings().t(nameKey), "a" to min, "b" to max,
                )
            }
            return null
        }
        number(timeout, "name_timeout", 3, 300)?.let { return it }
        number(settle, "name_settle", 0, 60)?.let { return it }
        number(pingCount, "name_pings", 1, 20)?.let { return it }
        if (host.isBlank() || host.contains(' ')) return strings().t("bad_host")
        return null
    }

    // -- Runs --

    fun currentPlanTasks(): List<TestTask> {
        val s = _settings.value
        return TestPlan.buildTasks(
            _pops.value,
            s.toRunOptions(s.currentProfileName, _imported.value),
        )
    }

    fun prepareStart(): StartCheck {
        val s = _settings.value
        if (s.portsInOrder.isEmpty()) return StartCheck.Blocked("need_protocol")
        if (!_wgInstalled.value || !_wgPermission.value) return StartCheck.Blocked("need_wg")
        val tasks = currentPlanTasks()
        if (tasks.isEmpty()) return StartCheck.Blocked("need_imported")
        val stored = AppDeps.db.rowCount()
        if (!s.resume && stored > 0) return StartCheck.NeedFreshConfirm(stored)
        return StartCheck.Ready
    }

    fun startRun(clearFirst: Boolean, context: Context) {
        val s = _settings.value
        if (clearFirst) {
            AppDeps.db.clear()
            refreshResults()
        }
        val options = s.toRunOptions(s.currentProfileName, _imported.value)
        val tasks = TestPlan.buildTasks(_pops.value, options)
        if (tasks.isEmpty()) return
        EngineState.pendingRun = EngineState.PendingRun(tasks, options)
        context.startService(
            Intent(context, TestService::class.java)
                .setAction(TestService.ACTION_START_RUN),
        )
    }

    fun stopRun(context: Context) {
        context.startService(
            Intent(context, TestService::class.java).setAction(TestService.ACTION_STOP),
        )
    }

    fun connectOne(result: TestResult, context: Context) {
        val pop = findPop(result) ?: return
        EngineState.pendingConnect = TestTask(pop, result.port)
        context.startService(
            Intent(context, TestService::class.java)
                .setAction(TestService.ACTION_CONNECT_ONE),
        )
    }

    fun connectTask(task: TestTask, context: Context) {
        EngineState.pendingConnect = task
        context.startService(
            Intent(context, TestService::class.java)
                .setAction(TestService.ACTION_CONNECT_ONE),
        )
    }

    fun disconnect(context: Context) {
        context.startService(
            Intent(context, TestService::class.java)
                .setAction(TestService.ACTION_DISCONNECT),
        )
    }

    fun probe(task: TestTask, context: Context) {
        EngineState.pendingProbe = task
        context.startService(
            Intent(context, TestService::class.java).setAction(TestService.ACTION_PROBE),
        )
    }

    private fun findPop(result: TestResult): Pop? {
        val key = "${result.country}|${result.city}|${result.location}"
        return _pops.value.firstOrNull { it.key == key }
            ?: Pop(result.country, "", "", result.city, result.location, false, false)
    }

    // -- Results --

    fun clearResults() {
        AppDeps.db.clear()
        refreshResults()
    }

    fun exportCsv(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rows = AppDeps.db.fetchAll()
                val csv = CsvExport.buildCsv(rows)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("could not open output stream")
                val s = strings()
                toast(s.t("exported", "n" to s.num(rows.size), "f" to (pendingExportName ?: "CSV")))
            } catch (e: Exception) {
                toast(strings().t("csv_failed", "e" to (e.message ?: "")))
            }
        }
    }

    private var pendingExportName: String? = null

    /** File name suggested to the file picker (desktop numbering pattern).
     *  The same name is reported in the "exported" toast, so the toast
     *  always names the file the user was offered. */
    fun prepareExportFileName(): String {
        val number = AppDeps.settings.nextExportNumber()
        val profile = _settings.value.currentProfileName.ifEmpty { "Manual" }
        return CsvExport.fileName(number, profile).also { pendingExportName = it }
    }

    // -- Profiles --

    fun saveProfile(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        AppDeps.settings.saveProfile(trimmed, _settings.value.copy(currentProfileName = trimmed))
        updateSettings { it.copy(currentProfileName = trimmed) }
        _profileNames.value = AppDeps.settings.profileNames()
        toast(strings().t("profile_saved", "n" to trimmed))
    }

    fun loadProfile(name: String) {
        val loaded = AppDeps.settings.loadProfile(name) ?: return
        _settings.value = loaded
        AppDeps.settings.saveSettings(loaded)
        toast(strings().t("profile_loaded", "n" to name))
    }

    fun deleteProfile(name: String) {
        AppDeps.settings.deleteProfile(name)
        _profileNames.value = AppDeps.settings.profileNames()
        if (_settings.value.currentProfileName == name) {
            updateSettings { it.copy(currentProfileName = "") }
        }
        toast(strings().t("profile_deleted", "n" to name))
    }

    // -- Imported tunnels --

    fun setImported(name: String, isImported: Boolean) {
        val next = _imported.value.toMutableSet()
        if (isImported) next.add(name) else next.remove(name)
        _imported.value = next
        AppDeps.settings.saveImported(next)
    }

    fun setImportedMany(names: Collection<String>, isImported: Boolean) {
        val next = _imported.value.toMutableSet()
        if (isImported) next.addAll(names) else next.removeAll(names.toSet())
        _imported.value = next
        AppDeps.settings.saveImported(next)
    }

    // -- Server list import --

    fun importLocations(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("could not open file")
                val pops = ServerListImport.parseBytes(bytes)
                ServerListImport.saveImported(context, bytes)
                AppDeps.settings.importedListTimestamp = System.currentTimeMillis()
                _pops.value = pops
                _importedListAt.value = AppDeps.settings.importedListTimestamp
                // Drop selections that no longer exist (desktop selection_gone:
                // an empty selection means "all", so this degrades gracefully).
                val keys = pops.map { it.key }.toSet()
                val kept = _settings.value.selectedKeys.filter { it in keys }.toSet()
                if (kept.size != _settings.value.selectedKeys.size) {
                    updateSettings { it.copy(selectedKeys = kept) }
                }
                val countries = pops.map { it.country }.toSet().size
                val s = strings()
                toast(s.t("import_list_ok", "n" to s.num(pops.size), "c" to s.num(countries)))
            } catch (e: Exception) {
                toast(strings().t("import_list_failed", "e" to (e.message ?: "")))
            }
        }
    }

    fun openWireGuardApp(context: Context) {
        AppDeps.wireGuard.launchAppIntent()?.let { context.startActivity(it) }
    }
}
