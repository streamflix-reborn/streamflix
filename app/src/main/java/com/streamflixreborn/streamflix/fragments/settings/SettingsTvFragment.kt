package com.streamflixreborn.streamflix.fragments.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreference
import com.streamflixreborn.streamflix.R
import com.streamflixreborn.streamflix.backup.BackupRestoreManager
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.database.dao.EpisodeDao
import com.streamflixreborn.streamflix.database.dao.MovieDao
import com.streamflixreborn.streamflix.database.dao.TvShowDao
import com.streamflixreborn.streamflix.providers.StreamingCommunityProvider
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsTvFragment : LeanbackPreferenceFragmentCompat() {

    private val DEFAULT_DOMAIN_VALUE = "streamingcommunityz.at"
    private val PREFS_ERROR_VALUE = "PREFS_NOT_INIT_ERROR"

    private lateinit var db: AppDatabase
    private lateinit var movieDao: MovieDao
    private lateinit var tvShowDao: TvShowDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var backupRestoreManager: BackupRestoreManager

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            viewLifecycleOwner.lifecycleScope.launch {
                performBackupExport(it)
            }
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewLifecycleOwner.lifecycleScope.launch {
                performBackupImport(it)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_tv, rootKey)

        db = AppDatabase.getInstance(requireContext())
        movieDao = db.movieDao()
        tvShowDao = db.tvShowDao()
        episodeDao = db.episodeDao()
        backupRestoreManager = BackupRestoreManager(requireContext(), movieDao, tvShowDao, episodeDao)

        displaySettings()
    }

    private fun displaySettings() {
        findPreference<PreferenceCategory>("pc_streamingcommunity_settings")?.apply {
            isVisible = UserPreferences.currentProvider is StreamingCommunityProvider
        }

        findPreference<EditTextPreference>("provider_streamingcommunity_domain")?.apply {
            val currentValue = UserPreferences.streamingcommunityDomain
            summary = currentValue
            if (currentValue == DEFAULT_DOMAIN_VALUE || currentValue == PREFS_ERROR_VALUE) {
                text = null
            } else {
                text = currentValue
            }
            setOnPreferenceChangeListener { preference, newValue ->
                val newDomainFromDialog = newValue as String
                UserPreferences.streamingcommunityDomain = newDomainFromDialog
                preference.summary = UserPreferences.streamingcommunityDomain
                if (UserPreferences.currentProvider is StreamingCommunityProvider) {
                    (UserPreferences.currentProvider as StreamingCommunityProvider).rebuildService()
                    requireActivity().apply {
                        finish()
                        startActivity(Intent(this, this::class.java))
                    }
                }
                true
            }
        }

        findPreference<Preference>("p_settings_about")?.apply {
            setOnPreferenceClickListener {
                Toast.makeText(requireContext(), "About screen for TV not yet implemented.", Toast.LENGTH_SHORT).show()
                true
            }
        }

        findPreference<SwitchPreference>("AUTOPLAY")?.apply {
            isChecked = UserPreferences.autoplay
            setOnPreferenceChangeListener { _, newValue ->
                UserPreferences.autoplay = newValue as Boolean
                true
            }
        }

        findPreference<ListPreference>("p_doh_provider_url")?.apply {
            value = UserPreferences.dohProviderUrl
            summary = entry
            setOnPreferenceChangeListener { preference, newValue ->
                val newUrl = newValue as String
                UserPreferences.dohProviderUrl = newUrl
                if (preference is ListPreference) {
                    val index = preference.findIndexOfValue(newUrl)
                    if (index >= 0 && preference.entries != null && index < preference.entries.size) {
                        preference.summary = preference.entries[index]
                    } else {
                        preference.summary = null
                    }
                }
                if (UserPreferences.currentProvider is StreamingCommunityProvider) {
                    (UserPreferences.currentProvider as StreamingCommunityProvider).rebuildService()
                    requireActivity().apply {
                        finish()
                        startActivity(Intent(this, this::class.java))
                    }
                }
                true
            }
        }

        val networkSettingsCategory = findPreference<PreferenceCategory>("pc_network_settings")
        if (networkSettingsCategory != null) {
            val originalTitle = getString(R.string.settings_category_network_title)
            val currentProviderName = UserPreferences.currentProvider?.name
            if (currentProviderName != null && currentProviderName.isNotEmpty()) {
                networkSettingsCategory.title = "$originalTitle $currentProviderName"
            } else {
                networkSettingsCategory.title = originalTitle
            }
        }

        findPreference<Preference>("key_backup_export_tv")?.setOnPreferenceClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "streamflix_tv_backup_$timestamp.json"
            exportBackupLauncher.launch(fileName)
            true
        }

        findPreference<Preference>("key_backup_import_tv")?.setOnPreferenceClickListener {
            importBackupLauncher.launch(arrayOf("application/json"))
            true
        }
    }

    private suspend fun performBackupExport(uri: Uri) {
        val jsonData = backupRestoreManager.exportUserData()
        if (jsonData != null) {
            try {
                requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.writer().use { it.write(jsonData) }
                    Toast.makeText(requireContext(), getString(R.string.backup_export_success), Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                Toast.makeText(requireContext(), getString(R.string.backup_export_error_write), Toast.LENGTH_LONG).show()
                Log.e("BackupExportTV", "Error writing backup file", e)
            }
        } else {
            Toast.makeText(requireContext(), getString(R.string.backup_data_not_generated), Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun performBackupImport(uri: Uri) {
        try {
            val stringBuilder = StringBuilder()
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { stringBuilder.append(it) }
                }
            }
            val jsonData = stringBuilder.toString()
            if (jsonData.isNotBlank()) {
                val success = backupRestoreManager.importUserData(jsonData)
                if (success) {
                    Toast.makeText(requireContext(), getString(R.string.backup_import_success), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.backup_import_error), Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.backup_import_empty_file), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.backup_import_read_error), Toast.LENGTH_LONG).show()
            Log.e("BackupImportTV", "Error reading/processing backup file", e)
        }
    }

    override fun onResume() {
        super.onResume()
        findPreference<PreferenceCategory>("pc_streamingcommunity_settings")?.isVisible =
            UserPreferences.currentProvider is StreamingCommunityProvider

        findPreference<EditTextPreference>("provider_streamingcommunity_domain")?.apply {
            val currentValue = UserPreferences.streamingcommunityDomain
            summary = currentValue
            if (currentValue == DEFAULT_DOMAIN_VALUE || currentValue == PREFS_ERROR_VALUE) {
                text = null
            } else {
                text = currentValue
            }
        }

        findPreference<ListPreference>("p_doh_provider_url")?.apply {
            summary = entry
        }

        val networkSettingsCategory = findPreference<PreferenceCategory>("pc_network_settings")
        if (networkSettingsCategory != null) {
            val originalTitle = getString(R.string.settings_category_network_title)
            val currentProviderName = UserPreferences.currentProvider?.name
            if (currentProviderName != null && currentProviderName.isNotEmpty()) {
                networkSettingsCategory.title = "$originalTitle $currentProviderName"
            } else {
                networkSettingsCategory.title = originalTitle
            }
        }
        findPreference<SwitchPreference>("AUTOPLAY")?.isChecked = UserPreferences.autoplay
        
        val bufferPref: EditTextPreference? = findPreference("p_settings_autoplay_buffer") 
        bufferPref?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
            val value = pref.text?.toLongOrNull() ?: 3L
            "$value s" // TODO: Estrarre "s" in strings.xml se necessario
        }
    }
}
