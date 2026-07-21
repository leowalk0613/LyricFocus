package com.leowalk.LyricFocus.xposed.hook.systemui

import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import com.leowalk.LyricFocus.notification.HyperFocusLyricStyle
import com.leowalk.LyricFocus.xposed.ReflectUtil
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Modifier

/**
 * 焦点通知置顶：检测数据层排位降低 → 回调 cancel + repost 重新置顶。
 * 不再直接操作 ViewGroup / List 排序，避免重型反射与布局抖动。
 */
object FocusPinAboveHook {

    private const val FOCUSED_NOTIF_CONTROLLER =
        "com.android.systemui.statusbar.phone.FocusedNotifPromptController"

    private const val REPOST_DEBOUNCE_MS = 3_000L
    private var lastRepostTime = 0L

    private val controllerListFields = listOf(
        "mNotifBeans",
        "mBeans",
        "mNotifList",
        "mDataList",
        "mFocusedNotifs",
        "mFocusNotifications",
        "mNotificationList",
        "mEntries",
        "mCountDownNotifBeans",
        "mCountDownBeans",
        "mTimerNotifBeans",
        "mTimerBeans",
        "mTopNotifBeans",
        "mPriorityNotifBeans",
        "mLiveNotifBeans",
        "mSortListEntries",
        "mPendingList"
    )

    fun install(classLoader: ClassLoader, module: XposedModule, tag: String) {
        hookFocusedNotifPromptController(classLoader, module, tag)
    }

    fun scheduleViewReorder(root: View?) {
        // 代替旧的 View 重排：不做操作，由 FocusPinState 回调触发 cancel+repost
    }

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
                    name.contains("update") ||
                    name.contains("bind") ||
                    name.contains("refresh") ||
                    name.contains("setdata") ||
                    name.contains("addnotif") ||
                    name.contains("removenotif") ||
                    name.contains("onposted") ||
                    name.contains("onremoved") ||
                    name.contains("prompt")
                if (!isRelevant) continue
                try {
                    module.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        if (FocusPinState.shouldPin()) {
                            checkLyricPositionAndTriggerRepost(chain.thisObject)
                        }
                        result
                    }
                    hooked++
                } catch (_: Throwable) {
                }
            }
            module.log(Log.INFO, tag, "FocusPin hooked $FOCUSED_NOTIF_CONTROLLER ($hooked methods, repost mode)")
        } catch (e: Throwable) {
            module.log(Log.INFO, tag, "FocusedNotifPromptController skipped: ${e.message}")
        }
    }

    private fun checkLyricPositionAndTriggerRepost(controller: Any) {
        val now = System.currentTimeMillis()
        if (now - lastRepostTime < REPOST_DEBOUNCE_MS) return

        val lists = findAllListsInObject(controller)
        for (list in lists) {
            val lyricIndex = list.indexOfFirst { isLyricListItem(it) }
            if (lyricIndex > 0) {
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
                ) {
                    return true
                }
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
        ) {
            return true
        }
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
