package com.leowalk.LyricFocus.xposed.hook.systemui

import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.leowalk.LyricFocus.notification.HyperFocusLyricStyle
import com.leowalk.LyricFocus.xposed.ReflectUtil
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Modifier

/**
 * 焦点通知置顶：检测视图层排位降低 → 回调 cancel + repost 重新置顶。
 * 数据层排序不反映 HyperOS 的视觉顺序，必须在视图层检测。
 */
object FocusPinAboveHook {

    private const val FOCUSED_NOTIF_CONTROLLER =
        "com.android.systemui.statusbar.phone.FocusedNotifPromptController"

    private const val FOCUSED_NOTIF_VIEW =
        "com.android.systemui.statusbar.phone.FocusedNotifPromptView"

    private const val REPOST_DEBOUNCE_MS = 3_000L
    private var lastRepostTime = 0L

    private val controllerListFields = listOf(
        "mNotifBeans", "mBeans", "mNotifList", "mDataList",
        "mFocusedNotifs", "mFocusNotifications", "mNotificationList",
        "mEntries", "mCountDownNotifBeans", "mCountDownBeans",
        "mTimerNotifBeans", "mTimerBeans", "mTopNotifBeans",
        "mPriorityNotifBeans", "mLiveNotifBeans", "mSortListEntries",
        "mPendingList"
    )

    fun install(classLoader: ClassLoader, module: XposedModule, tag: String) {
        hookFocusedNotifPromptView(classLoader, module, tag)
        hookFocusedNotifPromptController(classLoader, module, tag)
    }

    fun scheduleViewReorder(root: View?) {
    }

    /**
     * 兜底检测：positionClockAndNotifications 回流后检查通知栈中歌词视图排位。
     * 由 SystemUIHyperFocusHook 在面板布局完成后调用。
     */
    fun checkPanelAfterReflow(panelController: Any) {
        if (!FocusPinState.shouldPin()) return
        val now = System.currentTimeMillis()
        if (now - lastRepostTime < REPOST_DEBOUNCE_MS) return
        // 从 panelController -> mView -> 通知栈查找歌词视图位置
        val panelView = try {
            ReflectUtil.getField(panelController, "mView") as? ViewGroup
        } catch (_: Throwable) {
            null
        } ?: return
        val lyricView = findLyricViewInGroup(panelView) ?: return
        // 检查歌词视图在其直接父容器中的索引
        val parent = lyricView.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(lyricView)
        if (index > 0) {
            lastRepostTime = now
            FocusPinState.onRepostRequested?.invoke()
        }
    }

    // ── 视图层检测：FocusedNotifPromptView.addView / setData ──

    private fun hookFocusedNotifPromptView(
        classLoader: ClassLoader, module: XposedModule, tag: String
    ) {
        try {
            val clazz = ReflectUtil.findClass(FOCUSED_NOTIF_VIEW, classLoader)
            val methodNames = listOf("addView", "addViewInLayout", "setData", "bind", "update", "refresh", "show", "onDataChanged")
            var hooked = 0
            for (methodName in methodNames) {
                val methods = ReflectUtil.findMethodsByName(clazz, methodName)
                for (method in methods) {
                    try {
                        module.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            if (FocusPinState.shouldPin()) {
                                val view = chain.thisObject as? ViewGroup
                                if (view != null) {
                                    checkViewPositionAndTriggerRepost(view)
                                }
                            }
                            result
                        }
                        hooked++
                    } catch (_: Throwable) {
                    }
                }
            }
            module.log(Log.INFO, tag, "FocusPin hooked $FOCUSED_NOTIF_VIEW ($hooked methods, view check)")
        } catch (e: Throwable) {
            module.log(Log.INFO, tag, "FocusedNotifPromptView skipped: ${e.message}")
        }
    }

    private fun checkViewPositionAndTriggerRepost(container: ViewGroup) {
        val now = System.currentTimeMillis()
        if (now - lastRepostTime < REPOST_DEBOUNCE_MS) return

        val lyricView = findLyricViewInGroup(container) ?: return
        val index = container.indexOfChild(lyricView)
        if (index > 0) {
            lastRepostTime = now
            FocusPinState.onRepostRequested?.invoke()
        }
    }

    private fun findLyricViewInGroup(container: ViewGroup): View? {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (isLyricFocusView(child)) return child
            if (child is ViewGroup) {
                findLyricViewInGroup(child)?.let { return it }
            }
        }
        return null
    }

    private fun isLyricFocusView(view: View): Boolean {
        val sbn = extractSbnFromView(view)
        return sbn != null && isLyricFocusSbn(sbn)
    }

    private fun extractSbnFromView(view: View): StatusBarNotification? {
        return try {
            val entry = ReflectUtil.callMethod(view, "getEntry") ?: return null
            ReflectUtil.getField(entry, "mSbn") as? StatusBarNotification
        } catch (_: Throwable) {
            null
        }
    }

    // ── 数据层检测（辅助）：FocusedNotifPromptController ──

    private fun hookFocusedNotifPromptController(
        classLoader: ClassLoader, module: XposedModule, tag: String
    ) {
        try {
            val clazz = ReflectUtil.findClass(FOCUSED_NOTIF_CONTROLLER, classLoader)
            var hooked = 0
            for (method in clazz.declaredMethods) {
                if (Modifier.isStatic(method.modifiers)) continue
                val name = method.name.lowercase()
                if (name == "tostring" || name == "hashcode" || name == "equals") continue
                val isRelevant = name.contains("sort") ||
                    name.contains("update") || name.contains("bind") ||
                    name.contains("refresh") || name.contains("setdata") ||
                    name.contains("addnotif") || name.contains("removenotif") ||
                    name.contains("onposted") || name.contains("onremoved") ||
                    name.contains("prompt")
                if (!isRelevant) continue
                try {
                    module.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        if (FocusPinState.shouldPin()) {
                            checkControllerPositionAndTriggerRepost(chain.thisObject)
                        }
                        result
                    }
                    hooked++
                } catch (_: Throwable) {
                }
            }
            module.log(Log.INFO, tag, "FocusPin hooked $FOCUSED_NOTIF_CONTROLLER ($hooked methods, data check)")
        } catch (e: Throwable) {
            module.log(Log.INFO, tag, "FocusedNotifPromptController skipped: ${e.message}")
        }
    }

    private fun checkControllerPositionAndTriggerRepost(controller: Any) {
        val now = System.currentTimeMillis()
        if (now - lastRepostTime < REPOST_DEBOUNCE_MS) return
        val lists = findAllListsInObject(controller)
        for (list in lists) {
            if (list.indexOfFirst { isLyricListItem(it) } > 0) {
                lastRepostTime = now
                FocusPinState.onRepostRequested?.invoke()
                return
            }
        }
    }

    private fun findAllListsInObject(target: Any): List<MutableList<Any?>> {
        val result = mutableListOf<MutableList<Any?>>()
        for (fieldName in controllerListFields) {
            try {
                @Suppress("UNCHECKED_CAST")
                val list = ReflectUtil.getField(target, fieldName) as? MutableList<Any?>
                if (list != null && list !in result) result.add(list)
            } catch (_: Throwable) {
            }
        }
        for (field in target.javaClass.declaredFields) {
            if (!MutableList::class.java.isAssignableFrom(field.type)) continue
            try {
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val list = field.get(target) as? MutableList<Any?> ?: continue
                if (list !in result) result.add(list)
            } catch (_: Throwable) {
            }
        }
        return result
    }

    private fun isLyricListItem(item: Any?): Boolean {
        if (item == null) return false
        extractStatusBarNotification(item)?.let { return isLyricFocusSbn(it) }
        return isLyricNotifKey(item)
    }

    private fun isLyricNotifKey(item: Any): Boolean {
        for (fieldName in listOf("mKey", "key", "mNotificationKey", "notificationKey")) {
            try {
                val key = ReflectUtil.getField(item, fieldName)?.toString() ?: continue
                if (key.contains(HyperFocusLyricStyle.CHANNEL_ID) ||
                    key.contains("focusNotifLyrics")
                ) return true
            } catch (_: Throwable) {
            }
        }
        try {
            val id = ReflectUtil.getField(item, "mId") as? Int
            if (id == HyperFocusLyricStyle.CHANNEL_ID.hashCode()) return true
        } catch (_: Throwable) {
        }
        try {
            val id = ReflectUtil.getField(item, "id") as? Int
            if (id == HyperFocusLyricStyle.CHANNEL_ID.hashCode()) return true
        } catch (_: Throwable) {
        }
        return false
    }

    private fun isLyricFocusSbn(sbn: StatusBarNotification): Boolean {
        val channelId = sbn.notification?.channelId
        if (channelId == HyperFocusLyricStyle.CHANNEL_ID) return true
        val extras = sbn.notification?.extras ?: return false
        if (extras.containsKey("miui.focus.param.custom") &&
            sbn.packageName == HyperFocusLyricStyle.MODULE_PACKAGE
        ) return true
        val key = sbn.key.orEmpty()
        return key.contains(HyperFocusLyricStyle.CHANNEL_ID) || key.contains("focusNotifLyrics")
    }

    private fun extractStatusBarNotification(item: Any): StatusBarNotification? {
        if (item is StatusBarNotification) return item
        try {
            val direct = ReflectUtil.callMethod(item, "getSbn") as? StatusBarNotification
            if (direct != null) return direct
        } catch (_: Throwable) {
        }
        try {
            val entry = ReflectUtil.callMethod(item, "getEntry") ?: return null
            return ReflectUtil.getField(entry, "mSbn") as? StatusBarNotification
        } catch (_: Throwable) {
        }
        for (fieldName in listOf("mEntry", "entry", "mNotificationEntry")) {
            try {
                val entry = ReflectUtil.getField(item, fieldName) ?: continue
                val sbn = ReflectUtil.getField(entry, "mSbn") as? StatusBarNotification
                if (sbn != null) return sbn
            } catch (_: Throwable) {
            }
        }
        for (field in item.javaClass.declaredFields) {
            if (!StatusBarNotification::class.java.isAssignableFrom(field.type)) continue
            try {
                field.isAccessible = true
                return field.get(item) as? StatusBarNotification
            } catch (_: Throwable) {
            }
        }
        return null
    }
}
