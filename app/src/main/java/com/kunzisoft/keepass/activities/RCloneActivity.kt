package com.kunzisoft.keepass.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kunzisoft.keepass.R
import com.kunzisoft.keepass.activities.fragments.RClonePreferenceFragment
import com.kunzisoft.keepass.activities.legacy.DatabaseLockActivity
import com.kunzisoft.keepass.database.ContextualDatabase
import com.kunzisoft.keepass.settings.NestedSettingsFragment
import com.kunzisoft.keepass.settings.PreferencesUtil
import com.kunzisoft.keepass.tasks.ActionRunnable
import com.kunzisoft.keepass.timeout.TimeoutHelper
import com.kunzisoft.keepass.view.showActionErrorIfNeeded


class RCloneActivity : DatabaseLockActivity() {

  private var coordinatorLayout: CoordinatorLayout? = null
  private var lockView: FloatingActionButton? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_toolbar)
    coordinatorLayout = findViewById(R.id.toolbar_coordinator)

    lockView = findViewById(R.id.lock_button)
    lockView?.setOnClickListener {
      lockAndExit()
    }

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    toolbar.title = getString(R.string.menu_rclone)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.setDisplayShowHomeEnabled(true)


    if (savedInstanceState == null) {
      lockView?.visibility = View.GONE
      supportFragmentManager.beginTransaction()
        .add(R.id.fragment_container, RClonePreferenceFragment())
        .commit()
    } else {
      if (savedInstanceState.getBoolean(SHOW_LOCK)) lockView?.show() else lockView?.hide()
    }
  }

  override fun viewToInvalidateTimeout(): View? {
    return coordinatorLayout
  }

  override fun finishActivityIfDatabaseNotLoaded(): Boolean {
    return false
  }

  override fun onDatabaseActionFinished(
    database: ContextualDatabase,
    actionTask: String,
    result: ActionRunnable.Result
  ) {
    super.onDatabaseActionFinished(database, actionTask, result)

    coordinatorLayout?.showActionErrorIfNeeded(result)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      android.R.id.home -> onDatabaseBackPressed()
    }

    return super.onOptionsItemSelected(item)
  }

  private fun hideOrShowLockButton(key: NestedSettingsFragment.Screen) {
    if (PreferencesUtil.showLockDatabaseButton(this)) {
      when (key) {
        NestedSettingsFragment.Screen.DATABASE,
        NestedSettingsFragment.Screen.DATABASE_MASTER_KEY,
        NestedSettingsFragment.Screen.DATABASE_SECURITY -> {
          lockView?.show()
        }

        else -> {
          lockView?.hide()
        }
      }
    } else {
      lockView?.hide()
    }
  }

  override fun onDatabaseBackPressed() {
    // this if statement is necessary to navigate through nested and main fragments
    if (supportFragmentManager.backStackEntryCount == 0) {
      super.onDatabaseBackPressed()
    } else {
      supportFragmentManager.popBackStack()
    }
    hideOrShowLockButton(NestedSettingsFragment.Screen.APPLICATION)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(SHOW_LOCK, lockView?.visibility == View.VISIBLE)
  }

  fun getDatabase(): ContextualDatabase? {
    return mDatabase
  }

  companion object {
    private const val SHOW_LOCK = "SHOW_LOCK"

    fun launch(activity: Activity, timeoutEnable: Boolean) {
      val intent = Intent(activity, RCloneActivity::class.java)
      intent.putExtra(TIMEOUT_ENABLE_KEY, timeoutEnable)
      if (!timeoutEnable) {
        activity.startActivity(intent)
      } else if (TimeoutHelper.checkTimeAndLockIfTimeout(activity)) {
        activity.startActivity(intent)
      }
    }
  }
}