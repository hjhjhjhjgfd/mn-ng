package com.v2ray.ang

import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import com.tencent.mmkv.MMKV
import com.v2ray.ang.util.AppConfig
import com.v2ray.ang.util.MmkvManager

class AngApplication : MultiDexApplication() {
    companion object {
        const val PREF_LAST_VERSION = "pref_last_version"
        /**
         * Bumping this flag forces a one-shot cleanup of legacy
         * porn-filter rules that may still live in the user's
         * stored routing-blocked preference. Bump again whenever
         * we ship a new cleanup pass.
         */
        const val PREF_LEGACY_FILTER_CLEANUP_VERSION = "pref_legacy_filter_cleanup_version"
        const val LEGACY_FILTER_CLEANUP_VERSION_CODE = 1
    }

    var firstRun = false
        private set

    override fun onCreate() {
        super.onCreate()

//        LeakCanary.install(this)

        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        firstRun = defaultSharedPreferences.getInt(PREF_LAST_VERSION, 0) != BuildConfig.VERSION_CODE
        if (firstRun)
            defaultSharedPreferences.edit().putInt(PREF_LAST_VERSION, BuildConfig.VERSION_CODE).apply()

        //Logger.init().logLevel(if (BuildConfig.DEBUG) LogLevel.FULL else LogLevel.NONE)
        MMKV.initialize(this)

        // One-shot cleanup of legacy porn / NSFW filter rules that
        // older versions of the app baked into the user's stored
        // "blocked" routing list. Users explicitly asked for these
        // filters to be removed so they can access adult content
        // through the client. Idempotent - only runs once per
        // LEGACY_FILTER_CLEANUP_VERSION_CODE bump.
        runLegacyPornFilterCleanup()
    }

    /**
     * Strip every porn / NSFW related entry from the stored
     * PREF_V2RAY_ROUTING_BLOCKED list. Runs once per
     * LEGACY_FILTER_CLEANUP_VERSION_CODE bump.
     */
    private fun runLegacyPornFilterCleanup() {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(this)
            val lastCleanup = sp.getInt(PREF_LEGACY_FILTER_CLEANUP_VERSION, 0)
            if (lastCleanup >= LEGACY_FILTER_CLEANUP_VERSION_CODE) {
                return
            }

            // Patterns we want to drop from the blocked list. Cover
            // both the geosite:category-porn rule (legacy v2rayNG
            // default) and the ext:geosite_c4u.dat:nsfw rule used
            // by older MahsaNG builds.
            val bannedPatterns = listOf(
                "geosite:category-porn",
                "ext:geosite_c4u.dat:nsfw",
                "ext:geosite_c4u.dat:category-porn",
                "ext:geosite_c4u.dat:category-nsfw",
                "geosite:category-nsfw",
                "geosite:nsfw"
            )

            // SharedPreferences copy (used by the PreferenceFragment)
            val spBlocked = sp.getString(AppConfig.PREF_V2RAY_ROUTING_BLOCKED, "") ?: ""
            val cleanedSp = cleanRoutingList(spBlocked, bannedPatterns)
            if (cleanedSp != spBlocked) {
                sp.edit().putString(AppConfig.PREF_V2RAY_ROUTING_BLOCKED, cleanedSp).apply()
            }

            // MMKV copy (used at runtime by V2rayConfigUtil)
            val settingsStorage = MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE)
            val mmkvBlocked = settingsStorage?.decodeString(AppConfig.PREF_V2RAY_ROUTING_BLOCKED) ?: ""
            val cleanedMmkv = cleanRoutingList(mmkvBlocked, bannedPatterns)
            if (cleanedMmkv != mmkvBlocked) {
                settingsStorage?.encode(AppConfig.PREF_V2RAY_ROUTING_BLOCKED, cleanedMmkv)
            }

            // Mark the cleanup as done so we don't re-run it on
            // every cold start.
            sp.edit()
                .putInt(PREF_LEGACY_FILTER_CLEANUP_VERSION, LEGACY_FILTER_CLEANUP_VERSION_CODE)
                .apply()
        } catch (_: Throwable) {
            // Defensive: never break app startup over a cleanup pass.
        }
    }

    /**
     * Remove every entry matching any of [bannedPatterns] from a
     * comma-separated routing list. Preserves the original
     * separator style (comma + newline) so the UI looks the same
     * to the user.
     */
    private fun cleanRoutingList(raw: String, bannedPatterns: List<String>): String {
        if (raw.isBlank()) return raw
        val kept = raw.split(",", "\n", "\r\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { entry ->
                bannedPatterns.none { bad -> entry.equals(bad, ignoreCase = true) }
            }
        return if (kept.isEmpty()) {
            ""
        } else {
            // Re-join with a comma + newline so the RoutingSettings
            // EditText still shows one entry per line.
            kept.joinToString(separator = ",\n") + ","
        }
    }
}
