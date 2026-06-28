package com.leowalk.LyricFocus

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leowalk.LyricFocus.util.InstalledAppsHelper

class AppWhitelistActivity : AppCompatActivity() {

    private lateinit var adapter: WhitelistAdapter
    private val packages = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_app_whitelist)
        setupWindowInsets()
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        packages.addAll(FocusPreferences.getWhitelistedPackages(this))

        adapter = WhitelistAdapter(packages) { pkg ->
            packages.remove(pkg)
            adapter.notifyDataSetChanged()
            persistAndNotify()
        }

        findViewById<RecyclerView>(R.id.recycler_whitelist).apply {
            layoutManager = LinearLayoutManager(this@AppWhitelistActivity)
            adapter = this@AppWhitelistActivity.adapter
        }

        findViewById<MaterialButton>(R.id.btn_add_app).setOnClickListener {
            showAppPicker()
        }

        findViewById<MaterialButton>(R.id.btn_reset_defaults).setOnClickListener {
            packages.clear()
            packages.addAll(FocusPreferences.defaultMusicPackages())
            adapter.notifyDataSetChanged()
            persistAndNotify()
        }
    }

    private fun setupWindowInsets() {
        val appBar = findViewById<android.view.View>(R.id.app_bar)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, view.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(appBar)
        val content = findViewById<android.view.View>(R.id.whitelist_content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom)
            insets
        }
    }

    private fun showAppPicker() {
        val apps = InstalledAppsHelper.loadLaunchableApps(this)
            .filter { it.packageName !in packages }
        if (apps.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setMessage("没有可添加的应用")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val labels = apps.map { it.label }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("选择应用")
            .setItems(labels) { _, which ->
                packages.add(apps[which].packageName)
                adapter.notifyDataSetChanged()
                persistAndNotify()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun persistAndNotify() {
        FocusPreferences.setWhitelistedPackages(this, packages)
        val base = Intent(FocusPreferences.ACTION_SETTINGS_CHANGED).apply {
            putExtra(FocusPreferences.EXTRA_APP_WHITELIST_ENABLED, FocusPreferences.isAppWhitelistEnabled(this@AppWhitelistActivity))
        }
        sendBroadcast(Intent(base).setPackage("com.android.systemui"))
        sendBroadcast(Intent(base).setPackage(packageName))
    }

    private class WhitelistAdapter(
        private val packages: LinkedHashSet<String>,
        private val onRemove: (String) -> Unit
    ) : RecyclerView.Adapter<WhitelistAdapter.Holder>() {

        private val items: List<String>
            get() = packages.toList()

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.iv_app_icon)
            val label: TextView = view.findViewById(R.id.tv_app_label)
            val pkg: TextView = view.findViewById(R.id.tv_app_package)
            val remove: View = view.findViewById(R.id.btn_remove)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_whitelist_app, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val packageName = items[position]
            val context = holder.itemView.context
            holder.label.text = InstalledAppsHelper.labelFor(context, packageName)
            holder.pkg.text = packageName
            holder.icon.setImageDrawable(
                InstalledAppsHelper.iconFor(context, packageName)
            )
            holder.remove.setOnClickListener { onRemove(packageName) }
        }

        override fun getItemCount(): Int = items.size
    }
}
