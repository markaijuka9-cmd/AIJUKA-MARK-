// File 9: app/src/main/java/com/yourname/aerolauncher/MainActivity.kt
package com.yourname.aerolauncher

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

// Device Admin receiver class
class LockScreenAdminReceiver : android.app.admin.DeviceAdminReceiver()

class MainActivity : AppCompatActivity() {

    private lateinit var searchEditText: EditText
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var adapter: AppAdapter
    private lateinit var gestureDetector: GestureDetector

    // List of all installed apps
    private val allApps = mutableListOf<AppInfo>()
    private val filteredApps = mutableListOf<AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        searchEditText = findViewById(R.id.searchEditText)
        appsRecyclerView = findViewById(R.id.appsRecyclerView)

        // Set up RecyclerView with a 4‑column grid
        appsRecyclerView.layoutManager = GridLayoutManager(this, 4)
        adapter = AppAdapter(filteredApps) { app ->
            launchApp(app)
        }
        appsRecyclerView.adapter = adapter

        // Listen for search text changes
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterApps(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Gesture detector for double‑tap → lock screen
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                lockScreen()
                return true
            }
        })

        // Attach gesture detector to the root view
        findViewById<android.view.View>(R.id.rootLayout).setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        // Load all installed apps
        loadApps()
    }

    /**
     * Loads all launchable apps from the system, skipping our own launcher.
     */
    private fun loadApps() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, 0)

        allApps.clear()
        for (ri in resolved) {
            val ai = ri.activityInfo
            val pkg = ai.packageName
            // Exclude this launcher from the list
            if (pkg == packageName) continue
            val cls = ai.name
            val label = ri.loadLabel(pm).toString()
            val icon: Drawable? = ri.loadIcon(pm)
            allApps.add(AppInfo(pkg, cls, label, icon))
        }
        // Sort alphabetically
        allApps.sortBy { it.label.lowercase() }
        // Initial display
        filterApps("")
    }

    /**
     * Filters the allApps list by the query string and updates the RecyclerView.
     */
    private fun filterApps(query: String) {
        filteredApps.clear()
        if (query.isBlank()) {
            filteredApps.addAll(allApps)
        } else {
            val q = query.lowercase()
            filteredApps.addAll(allApps.filter { it.label.lowercase().contains(q) })
        }
        adapter.notifyDataSetChanged()
    }

    /**
     * Launches the selected app via its component name.
     */
    private fun launchApp(app: AppInfo) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setClassName(app.packageName, app.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Uses DevicePolicyManager to lock the screen immediately.
     * If admin is not enabled, the user is prompted to enable it.
     */
    private fun lockScreen() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, LockScreenAdminReceiver::class.java)

        if (dpm.isAdminActive(adminComponent)) {
            dpm.lockNow()
        } else {
            // Ask the user to enable Device Admin
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Enable screen lock permission to lock your phone with a double‑tap.")
            }
            startActivity(intent)
        }
    }
}
