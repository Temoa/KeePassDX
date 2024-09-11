package com.kunzisoft.keepass.activities.fragments

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import ca.pkay.rcloneexplorer.Items.FileItem
import ca.pkay.rcloneexplorer.Items.RemoteItem
import com.kunzisoft.keepass.R
import com.kunzisoft.keepass.activities.RCloneActivity
import com.kunzisoft.keepass.activities.helpers.ExternalFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.temoa.rclone.Rclone
import org.joda.time.DateTime
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


class RClonePreferenceFragment : PreferenceFragmentCompat() {

  private var externalFileHelper: ExternalFileHelper? = null
  private val rClone: Rclone by lazy { Rclone(requireContext()) }
  private var remote: RemoteItem? = null
  private val fileItems: MutableList<FileItem> = mutableListOf()
  private var currentNeedSaveFile: File? = null

  private var rCloneImportConfigPreference: Preference? = null
  private var rCloneConfigPreference: Preference? = null
  private var rCloneBackupPreference: Preference? = null
  private var rCloneDownloadPreference: Preference? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    externalFileHelper = ExternalFileHelper(this)
    externalFileHelper?.buildOpenDocument { uri ->
      if (uri == null) return@buildOpenDocument
      saveConfig(uri)
    }
    externalFileHelper?.buildCreateDocument("application/x-keepass") { uri ->
      if (uri == null) return@buildCreateDocument
      saveBackupFileTo(uri)
    }
    getRemotes()
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_rclone, rootKey)

    rCloneImportConfigPreference = findPreference<Preference>(getString(R.string.settings_rclone_import_config))?.apply {
      onPreferenceClickListener = Preference.OnPreferenceClickListener {
        externalFileHelper?.openDocument()
        false
      }
    }
    rCloneConfigPreference = findPreference<Preference?>(getString(R.string.settings_rclone_config))?.apply {
      onPreferenceClickListener = Preference.OnPreferenceClickListener {
        if (!rClone.hasRCloneConf()) {
          return@OnPreferenceClickListener false
        }
        AlertDialog.Builder(requireContext())
          .setTitle(R.string.menu_rclone_delete_title)
          .setMessage(R.string.menu_rclone_delete_message)
          .setPositiveButton(R.string.menu_rclone_delete_ok) { _, _ ->
            deleteConfig()
          }
          .setNegativeButton(R.string.menu_rclone_delete_cancel) { _, _ -> }
          .show()
        false
      }
    }
    rCloneBackupPreference = findPreference<Preference?>(getString(R.string.settings_rclone_backup))?.apply {
      onPreferenceClickListener = Preference.OnPreferenceClickListener {
        backup()
        false
      }
    }
    rCloneDownloadPreference = findPreference<Preference?>(getString(R.string.settings_rclone_download))?.apply {
      summary = getString(R.string.menu_rclone_download_backup_summary, 0)
      onPreferenceClickListener = Preference.OnPreferenceClickListener {
        downloadBackupFile()
        false
      }
    }
  }

  private fun saveConfig(uri: Uri) {
    val copyResult = rClone.copyConfigFile(uri)
    if (copyResult) {
      getRemotes()
    } else {
      showToast(getString(R.string.menu_rclone_config_is_void))
    }
  }

  private fun getRemotes() {
    if (!rClone.hasRCloneConf()) {
      return
    }

    lifecycleScope.launch(Dispatchers.IO) {
      withContext(Dispatchers.Main) {
        rCloneConfigPreference?.apply {
          summary = getString(R.string.menu_rclone_checking)
        }
      }

      val remotes = rClone.remotes
      if (!this.isActive) return@launch
      if (remotes.isEmpty()) {
        showToast(getString(R.string.menu_rclone_config_is_void))
        return@launch
      }
      remote = remotes[0]
      val aboutRemote = rClone.aboutRemote(remote)
      if (!this.isActive) return@launch
      val about = aboutRemote.toString()
        .replace("Total", getString(R.string.menu_rclone_config_about_total))
        .replace("Used", getString(R.string.menu_rclone_config_about_used))
        .replace("Free", getString(R.string.menu_rclone_config_about_free))
        .replace("Trashed", getString(R.string.menu_rclone_config_about_trashed))

      getBackupFiles(remote!!)

      withContext(Dispatchers.Main) {
        rCloneConfigPreference?.apply {
          icon = ContextCompat.getDrawable(requireContext(), remote!!.remoteIcon)
          title = getString(R.string.menu_rclone_import_config_imported)
          summary = "[${remote!!.typeReadable}] ${remote!!.displayName}\n$about"
        }
      }
    }
  }

  private fun getBackupFiles(remote: RemoteItem?) {
    if (remote == null) return
    lifecycleScope.launch(Dispatchers.IO) {
      withContext(Dispatchers.Main) {
        rCloneBackupPreference?.apply {
          summary = getString(R.string.menu_rclone_checking)
        }
        rCloneDownloadPreference?.apply {
          summary = getString(R.string.menu_rclone_checking)
        }
      }
      var files = rClone.getDirectoryContent(remote, DEFAULT_PATH, true) ?: return@launch
      if (!this.isActive) return@launch
      if (files.isNotEmpty()) {
        files = files.filter { it.name.startsWith(getString(R.string.database_file_name_default)) }
        files.sortByDescending { it.modTime }
        fileItems.clear()
        fileItems.addAll(files)
        val latestFile = files[0]
        withContext(Dispatchers.Main) {
          rCloneBackupPreference?.apply {
            summary = latestFile.humanReadableModTime
          }
          rCloneDownloadPreference?.apply {
            summary = getString(R.string.menu_rclone_download_backup_summary, files.size)
          }
        }
      } else {
        withContext(Dispatchers.Main) {
          rCloneBackupPreference?.apply {
            summary = getString(R.string.menu_rclone_backup_not)
          }
        }
        rCloneDownloadPreference?.apply {
          summary = getString(R.string.menu_rclone_download_backup_summary, files.size)
        }
      }
    }
  }

  private fun deleteConfig() {
    lifecycleScope.launch(Dispatchers.IO) {
      rClone.deleteRCloneConf()
      remote = null
      withContext(Dispatchers.Main) {
        rCloneConfigPreference?.apply {
          icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_rclone_cloud)
          title = ""
          summary = getString(R.string.menu_rclone_import_config_not_imported)
        }
        rCloneBackupPreference?.apply {
          summary = getString(R.string.menu_rclone_backup_not)
        }
      }
    }
  }

  private fun backup() {
    if (remote == null) {
      showToast(getString(R.string.menu_rclone_config_is_void))
      return
    }
    if ((requireActivity() as RCloneActivity).getDatabase() == null) {
      return
    }
    lifecycleScope.launch(Dispatchers.IO) {
      withContext(Dispatchers.Main) {
        rCloneBackupPreference?.apply {
          summary = getString(R.string.menu_rclone_backing_up)
        }
      }
      val dateTime = DateTime(System.currentTimeMillis())
      val fileName = StringBuilder(getString(R.string.database_file_name_default))
        .append("_")
        .append(dateTime.toString("yyyyMMddHHmmss"))
        .append((requireActivity() as RCloneActivity).getDatabase()?.defaultFileExtension)

      val fileUri = (requireActivity() as RCloneActivity).getDatabase()?.fileUri ?: return@launch
      val inputStream: InputStream?
      try {
        inputStream = requireContext().contentResolver.openInputStream(fileUri)
      } catch (e: NullPointerException) {
        Log.e(TAG, "backup: ", e)
        showToast(getString(R.string.menu_rclone_backup_failed))
        return@launch
      }
      val appsFileDir = requireContext().filesDir.path
      val tempFile = File(appsFileDir, fileName.toString())
      val fileOutputStream = FileOutputStream(tempFile)

      val buffer = ByteArray(4096)
      var offset: Int
      while ((inputStream!!.read(buffer).also { offset = it }) > 0) {
        fileOutputStream.write(buffer, 0, offset)
      }
      inputStream.close()
      fileOutputStream.flush()
      fileOutputStream.close()

      val process = rClone.uploadFile(remote, DEFAULT_PATH, tempFile.absolutePath)
      process.waitFor()
      if (this.isActive) {
        if (process.exitValue() != 0) {
          showToast(getString(R.string.menu_rclone_backup_failed))
        } else {
          showToast(getString(R.string.menu_rclone_backup_succeeded))
        }
        tempFile.delete()

        getBackupFiles(remote)
      }
    }
  }

  private fun downloadBackupFile() {
    if (remote == null) {
      showToast(getString(R.string.menu_rclone_config_is_void))
      return
    }
    if (fileItems.isEmpty()) return
    AlertDialog.Builder(requireContext())
      .setTitle(R.string.menu_rclone_download_backup_dialog_title)
      .setItems(fileItems.map { it.name }.toTypedArray()) { _, index ->
        val fileItem = fileItems[index]
        val downloadPath = requireContext().filesDir.path
        lifecycleScope.launch(Dispatchers.IO) {
          withContext(Dispatchers.Main) {
            rCloneDownloadPreference?.apply {
              summary = getString(R.string.menu_rclone_download_backup_downloading)
            }
          }
          val process = rClone.downloadFile(remote!!, fileItem, downloadPath)
          process.waitFor()
          if (this.isActive) {
            if (process.exitValue() != 0) {
              showToast(getString(R.string.menu_rclone_download_backup_failed))
            } else {
              currentNeedSaveFile = File(downloadPath, fileItem.name)
              if (currentNeedSaveFile!!.exists()) {
                externalFileHelper?.createDocument(fileItem.name)
              } else {
                currentNeedSaveFile = null
                showToast(getString(R.string.menu_rclone_download_backup_failed))
              }
            }
            withContext(Dispatchers.Main) {
              rCloneDownloadPreference?.apply {
                summary = getString(R.string.menu_rclone_download_backup_summary, fileItems.size)
              }
            }
          }
        }
      }
      .show()
  }

  private fun saveBackupFileTo(uri: Uri) {
    if (currentNeedSaveFile == null) return
    lifecycleScope.launch(Dispatchers.IO) {
      var outputStream: OutputStream? = null
      var inputStream: InputStream? = null
      try {
        outputStream = requireContext().contentResolver.openOutputStream(uri)
        inputStream = FileInputStream(currentNeedSaveFile!!)
        val buffer = ByteArray(4096)
        var offset: Int
        while ((inputStream.read(buffer).also { offset = it }) > 0) {
          outputStream!!.write(buffer, 0, offset)
        }
        outputStream!!.flush()
        showToast(getString(R.string.menu_rclone_download_backup_succeeded))
      } catch (e: IOException) {
        Log.e(TAG, "saveBackupFileTo: ", e)
        showToast(getString(R.string.menu_rclone_download_backup_failed))
      } finally {
        currentNeedSaveFile?.delete()
        currentNeedSaveFile = null
        inputStream?.close()
        outputStream?.close()
      }
    }
  }

  private fun showToast(msg: String) {
    requireActivity().runOnUiThread {
      Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
  }

  companion object {
    private const val TAG = "RClonePreferenceFragmen"
    private const val DEFAULT_PATH = "KeepassDX"
  }
}