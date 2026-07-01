package com.leowalk.LyricFocus

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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

        findViewById<MaterialButton>(R.id.btn_add_package).setOnClickListener {
            showAddPackageDialog()
        }

        findViewById<MaterialButton>(R.id.btn_reset_defaults).setOnClickListener {
            packages.clear()
            packages.addAll(FocusPreferences.defaultMusicPackages())
            adapter.notifyDataSetChanged()
            persistAndNotify()
        }
    }

    private fun setupWindowInsets() {
        val appBar = findViewById<View>(R.id.app_bar)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, view.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(appBar)
        val content = findViewById<View>(R.id.whitelist_content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom)
            insets
        }
    }

    private fun showAppPicker() {
        if (InstalledAppsHelper.hasLimitedPackageVisibility(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("应用列表可能不完整")
                .setMessage(
                    "系统可能限制了读取已安装应用。请在应用权限中开启「读取应用列表」，" +
                        "或直接使用「添加包名」。"
                )
                .setPositiveButton("去设置") { _, _ ->
                    InstalledAppsHelper.openAppListPermissionSettings(this)
                }
                .setNegativeButton("继续选择") { _, _ ->
                    showAppPickerDialog()
                }
                .show()
            return
        }
        showAppPickerDialog()
    }

    private fun showAppPickerDialog() {
        val allApps = InstalledAppsHelper.loadInstalledApps(this)
            .filter { it.packageName !in packages }
        if (allApps.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setMessage("没有可添加的应用")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_app_picker, null)
        val searchInput = dialogView.findViewById<TextInputEditText>(R.id.search_input)
        val pickerHint = dialogView.findViewById<TextView>(R.id.tv_picker_hint)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.app_picker_list)
        recycler.layoutManager = LinearLayoutManager(this)

        if (InstalledAppsHelper.hasLimitedPackageVisibility(this)) {
            pickerHint.visibility = View.VISIBLE
            pickerHint.text = "当前仅显示 ${allApps.size} 个应用，建议开启读取应用列表权限或使用添加包名。"
        }

        val pickerAdapter = AppPickerAdapter(allApps) { entry ->
            packages.add(entry.packageName)
            adapter.notifyDataSetChanged()
            persistAndNotify()
            pickerDialog?.dismiss()
        }
        recycler.adapter = pickerAdapter

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("选择应用")
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        pickerDialog = dialog

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                pickerAdapter.filter(s?.toString().orEmpty())
            }
        })

        dialog.show()
    }

    private var pickerDialog: androidx.appcompat.app.AlertDialog? = null

    private fun showAddPackageDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_package, null)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.package_input_layout)
        val input = dialogView.findViewById<TextInputEditText>(R.id.package_input)

        MaterialAlertDialogBuilder(this)
            .setTitle("添加包名")
            .setMessage("可手动输入音乐应用的包名，未安装的应用也会保留在白名单中。")
            .setView(dialogView)
            .setPositiveButton("添加", null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val packageName = input.text?.toString()?.trim().orEmpty()
                        when {
                            packageName.isBlank() -> {
                                inputLayout.error = "请输入包名"
                            }
                            !InstalledAppsHelper.isValidPackageName(packageName) -> {
                                inputLayout.error = "包名格式不正确"
                            }
                            packageName in packages -> {
                                inputLayout.error = "该包名已在白名单中"
                            }
                            else -> {
                                packages.add(packageName)
                                adapter.notifyDataSetChanged()
                                persistAndNotify()
                                val message = if (InstalledAppsHelper.isPackageInstalled(this, packageName)) {
                                    "已添加"
                                } else {
                                    "已添加（当前未检测到该应用）"
                                }
                                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun persistAndNotify() {
        FocusPreferences.setWhitelistedPackages(this, packages)
        val base = Intent(FocusPreferences.ACTION_SETTINGS_CHANGED).apply {
            putExtra(
                FocusPreferences.EXTRA_APP_WHITELIST_ENABLED,
                FocusPreferences.isAppWhitelistEnabled(this@AppWhitelistActivity)
            )
        }
        sendBroadcast(Intent(base).setPackage("com.android.systemui"))
        sendBroadcast(Intent(base).setPackage(packageName))
    }

    private class AppPickerAdapter(
        private val allApps: List<InstalledAppsHelper.AppEntry>,
        private val onPick: (InstalledAppsHelper.AppEntry) -> Unit
    ) : RecyclerView.Adapter<AppPickerAdapter.Holder>() {

        private var filtered = allApps.toList()

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.iv_app_icon)
            val label: TextView = view.findViewById(R.id.tv_app_label)
            val pkg: TextView = view.findViewById(R.id.tv_app_package)
        }

        fun filter(query: String) {
            val normalized = query.trim().lowercase()
            filtered = if (normalized.isEmpty()) {
                allApps
            } else {
                allApps.filter {
                    it.label.lowercase().contains(normalized) ||
                        it.packageName.lowercase().contains(normalized)
                }
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_picker_row, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = filtered[position]
            holder.label.text = entry.label
            holder.pkg.text = entry.packageName
            val icon = entry.icon
            if (icon != null) {
                holder.icon.setImageDrawable(icon)
            } else {
                holder.icon.setImageResource(R.mipmap.ic_launcher)
            }
            holder.itemView.setOnClickListener { onPick(entry) }
        }

        override fun getItemCount(): Int = filtered.size
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
            val icon = InstalledAppsHelper.iconFor(context, packageName)
            if (icon != null) {
                holder.icon.setImageDrawable(icon)
            } else {
                holder.icon.setImageResource(R.mipmap.ic_launcher)
            }
            holder.remove.setOnClickListener { onRemove(packageName) }
        }

        override fun getItemCount(): Int = items.size
    }
}
