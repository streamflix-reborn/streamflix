package com.streamflixreborn.streamflix.fragments.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.streamflixreborn.streamflix.R
import com.streamflixreborn.streamflix.backup.BackupRestoreManager
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.database.dao.EpisodeDao
import com.streamflixreborn.streamflix.database.dao.MovieDao
import com.streamflixreborn.streamflix.database.dao.TvShowDao
import com.streamflixreborn.streamflix.fragments.settings.SettingsMobileFragmentDirections
import com.streamflixreborn.streamflix.providers.StreamingCommunityProvider
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsMobileFragment : PreferenceFragmentCompat() {

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
        setPreferencesFromResource(R.xml.settings_mobile, rootKey)

        // Inizializzazione DB e Manager
        db = AppDatabase.getInstance(requireContext())
        movieDao = db.movieDao()
        tvShowDao = db.tvShowDao()
        episodeDao = db.episodeDao()
        backupRestoreManager = BackupRestoreManager(requireContext(), movieDao, tvShowDao, episodeDao)

        displaySettings()
    }

    private fun displaySettings() {
        // Gestione visibilità categoria StreamingCommunity
        findPreference<PreferenceCategory>("pc_streamingcommunity_settings")?.apply {
            isVisible = UserPreferences.currentProvider is StreamingCommunityProvider
        }
        findPreference<SwitchPreference>("AUTOPLAY")?.apply {
            isChecked = UserPreferences.autoplay
            setOnPreferenceChangeListener { _, newValue ->
                UserPreferences.autoplay = newValue as Boolean
                true
            }
        }

        findPreference<Preference>("p_settings_about")?.apply {
            setOnPreferenceClickListener {
                findNavController().navigate(
                    SettingsMobileFragmentDirections.actionSettingsToSettingsAbout()
                )
                true
            }
        }

        findPreference<Preference>("p_settings_help")?.apply {
            setOnPreferenceClickListener {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/streamflix-reborn/streamflix")
                    )
                )
                true
            }
        }

        // Rinominato per coerenza, se necessario provider_streamingcommunity_domain
        findPreference<EditTextPreference>("provider_streamingcommunity_domain")?.apply {
            summary = UserPreferences.streamingcommunityDomain

            setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_TEXT
                editText.imeOptions = EditorInfo.IME_ACTION_DONE
                editText.hint = "streamingcommunity.example"
                val pref = UserPreferences.streamingcommunityDomain
                if (!pref.isNullOrEmpty()) { // Modificato per non impostare testo se è vuoto
                    editText.setText(pref)
                } else {
                    editText.setText(null) // Assicura che l'hint sia mostrato se il pref è vuoto
                }
            }

            setOnPreferenceChangeListener { _, newValue ->
                val newDomain = newValue as String
                UserPreferences.streamingcommunityDomain = newDomain
                summary = UserPreferences.streamingcommunityDomain // Usa il valore effettivo da UserPreferences
                if (UserPreferences.currentProvider is StreamingCommunityProvider) {
                    (UserPreferences.currentProvider as StreamingCommunityProvider).rebuildService() // Rimosso newDomain, usa UserPreferences
                    requireActivity().apply {
                        finish()
                        startActivity(Intent(this, this::class.java))
                    }
                }
                true
            }
        }


        findPreference<ListPreference>("p_doh_provider_url")?.apply {
            value = UserPreferences.dohProviderUrl // Rimossa logica DOH_DISABLED_VALUE se non necessaria
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

        // Listener per Esporta Backup
        findPreference<Preference>("key_backup_export_mobile")?.setOnPreferenceClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "streamflix_mobile_backup_$timestamp.json"
            exportBackupLauncher.launch(fileName)
            true
        }

        // Listener per Importa Backup
        findPreference<Preference>("key_backup_import_mobile")?.setOnPreferenceClickListener {
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
                    Toast.makeText(requireContext(), "Backup esportato con successo!", Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                Toast.makeText(requireContext(), "Errore durante l'esportazione del backup.", Toast.LENGTH_LONG).show()
                Log.e("BackupExportMobile", "Error writing backup file", e)
            }
        } else {
            Toast.makeText(requireContext(), "Errore: dati di backup non generati.", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(requireContext(), "Backup importato con successo! Riavvia l'app per applicare le modifiche.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Errore durante l'importazione del backup.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), "Errore: file di backup vuoto o illeggibile.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Errore durante la lettura o l'elaborazione del file di backup.", Toast.LENGTH_LONG).show()
            Log.e("BackupImportMobile", "Error reading/processing backup file", e)
        }
    }

    override fun onResume() {
        super.onResume()
        findPreference<PreferenceCategory>("pc_streamingcommunity_settings")?.isVisible =
            UserPreferences.currentProvider is StreamingCommunityProvider
        findPreference<SwitchPreference>("AUTOPLAY")?.isChecked = UserPreferences.autoplay

        // Aggiorna summary per provider_streamingcommunity_domain in onResume
        findPreference<EditTextPreference>("provider_streamingcommunity_domain")?.summary = UserPreferences.streamingcommunityDomain

        // Aggiorna summary per p_doh_provider_url in onResume
        findPreference<ListPreference>("p_doh_provider_url")?.apply {
            summary = entry
        }


        // Mantenuto per p_settings_autoplay_buffer, se esiste ancora nel XML (non presente nell'ultimo XML mostrato)
        val bufferPref: EditTextPreference? = findPreference("p_settings_autoplay_buffer")
        bufferPref?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
            val value = pref.text?.toLongOrNull() ?: 3L
            "$value seconds" // TODO: Estrarre "seconds" in strings.xml
        }
    }
}
