package com.leowalk.LyricFocus.xposed.hook.systemui

import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import com.leowalk.LyricFocus.notification.HyperFocusLyricStyle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Modifier

/**
 * 在焦点通知数据层 / 列表层将歌词焦点通知置顶（高于其他焦点通知与 HyperIsland 转换通知）。
 * 仅操作 NotificationStackScrollLayout 子 View 顺序无法影响锁屏焦点卡片排序。
 */
object FocusPinAboveHook {

    private val pinGuard = ThreadLocal.withInitial { false }

    private const val FOCUSED_NOTIF_CONTROLLER =
        "com.android.systemui.statusbar.phone.FocusedNotifPromptController"

    private const val FOCUSED_NOTIF_VIEW =
        "com.android.systemui.statusbar.phone.FocusedNotifPromptView"

    private val focusPluginCandidates = listOf(
        "miui.systemui.notification.focus.FocusNotificationPluginImpl",
        "com.android.systemui.plugins.miui.notification.focus.FocusNotificationPluginImpl"
    )

    private val focusCoordinatorCandidates = listOf(
        "miui.systemui.notification.focus.FocusCoordinator",
        "miui.systemui.notification.focus.FocusNotificationCoordinator",
        "miui.systemui.notification.focus.FocusListController",
        "miui.systemui.notification.focus.FocusNotificationListController",
        "miui.systemui.notification.focus.FocusController",
        "miui.systemui.notification.focus.FocusNotificationManager"
    )

    private val focusContainerCandidates = listOf(
        "miui.systemui.notification.focus.FocusNotificationContainer",
        "miui.systemui.notification.focus.widget.FocusNotificationContainer",
        "miui.systemui.notification.focus.view.FocusNotificationContainer",
        "miui.systemui.notification.focus.FocusListContainer",
        "miui.systemui.notification.focus.widget.FocusNotificationListView",
        "miui.systemui.notification.focus.view.FocusNotificationListView",
        "com.miui.systemui.statusbar.notification.focus.FocusNotificationContainer",
        "com.android.systemui.statusbar.phone.FocusedNotifPromptView"
    )

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
        "mLiveNotifBeans"
    )

    private val countdownCoordinatorCandidates = listOf(
        "miui.systemui.notification.focus.FocusCountDownCoordinator",
        "miui.systemui.notification.focus.CountDownFocusCoordinator",
        "miui.systemui.notification.focus.TimerFocusCoordinator",
        "com.android.systemui.statusbar.phone.FocusCountDownController"
    )

    private val countdownContainerCandidates = listOf(
        "miui.systemui.notification.focus.CountDownFocusNotificationContainer",
        "miui.systemui.notification.focus.widget.CountDownFocusNotificationContainer",
        "miui.systemui.notification.focus.view.CountDownFocusNotificationView",
        "com.android.systemui.statusbar.phone.FocusedNotifCountDownView"
    )

    private inline fun runPinSafely(block: () -> Unit) {
        if (pinGuard.get() == true) return
        pinGuard.set(true)
        try {
            block()
        } catch (_: Throwable) {
        } finally {
            pinGuard.set(false)
        }
    }

    private fun shouldHookControllerMethod(method: java.lang.reflect.Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        val name = method.name.lowercase()
        if (name == "tostring" || name == "hashcode" || name == "equals") return false
        return name.contains("sort") ||
            name.contains("list") ||
            name.contains("bean") ||
            name.contains("update") ||
            name.contains("refresh") ||
            name.contains("bind") ||
            name.contains("addnotif") ||
            name.contains("removenotif") ||
            name.contains("setdata") ||
            name.contains("onposted") ||
            name.contains("onremoved") ||
            name.contains("prompt")
    }

    fun install(classLoader: ClassLoader, tag: String) {
        hookShadeListBuilder(classLoader, tag)
        hookFocusedNotifPromptController(classLoader, tag)
        hookFocusedNotifPromptView(classLoader, tag)
        hookFocusCoordinatorCandidates(classLoader, tag)
        hookFocusViewContainers(classLoader, tag)
        hookNotificationStackScrollLayout(classLoader, tag)
        hookFocusPluginImpl(classLoader, tag)
        hookCountdownFocusCandidates(classLoader, tag)
        hookFocusComparator(classLoader, tag)
        hookNotificationPanelReflow(classLoader, tag)
    }

    private fun hookFocusComparator(classLoader: ClassLoader, tag: String) {
        try {
            val clazz = XposedHelpers.findClass(FOCUSED_NOTIF_CONTROLLER, classLoader)
            for (inner in clazz.declaredClasses) {
                if (!Comparator::class.java.isAssignableFrom(inner)) continue
                XposedBridge.hookAllMethods(inner, "compare", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!FocusPinState.shouldPin()) return
                        val left = param.args.getOrNull(0)
                        val right = param.args.getOrNull(1)
                        val leftLyric = isLyricListItem(left)
                        val rightLyric = isLyricListItem(right)
                        when {
                            leftLyric && !rightLyric -> param.result = -1
                            !leftLyric && rightLyric -> param.result = 1
                            leftLyric && isCountdownListItem(right) -> param.result = -1
                            isCountdownListItem(left) && rightLyric -> param.result = 1
                        }
                    }
                })
                XposedBridge.log("$tag: FocusPin hooked comparator ${inner.name}")
            }
        } catch (e: Throwable) {
            XposedBridge.log("$tag: Focus comparator hook skipped: ${e.message}")
        }
    }

    private fun hookNotificationPanelReflow(classLoader: ClassLoader, tag: String) {
        val targets = listOf(
            "com.android.systemui.shade.MiuiNotificationPanelViewController",
            "com.android.systemui.shade.NotificationPanelViewController"
        )
        for (target in targets) {
            try {
                XposedHelpers.findAndHookMethod(
                    target,
                    classLoader,
                    "positionClockAndNotifications",
                    Boolean::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!FocusPinState.shouldPin()) return
                            try {
                                val view = XposedHelpers.getObjectField(param.thisObject, "mView") as? View
                                    ?: XposedHelpers.getObjectField(param.thisObject, "mNotificationContainerParent") as? View
                                if (view is ViewGroup) {
                                    view.post { demoteCountdownViewsAboveLyric(view) }
                                }
                            } catch (_: Throwable) {
                            }
                        }
                    }
                )
                XposedBridge.log("$tag: FocusPin hooked panel reflow on $target")
                break
            } catch (_: Throwable) {
            }
        }
    }

    private fun hookShadeListBuilder(classLoader: ClassLoader, tag: String) {
        val shadeBuilder = "com.android.systemui.statusbar.notification.collection.ShadeListBuilder"
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!FocusPinState.shouldPin()) return
                @Suppress("UNCHECKED_CAST")
                val list = param.args.getOrNull(0) as? MutableList<Any?> ?: return
                safePinLyricEntryToFront(list)
            }
        }
        try {
            XposedHelpers.findAndHookMethod(
                shadeBuilder,
                classLoader,
                "dispatchOnBeforeSort",
                List::class.java,
                hook
            )
            XposedBridge.log("$tag: FocusPin hooked ShadeListBuilder.dispatchOnBeforeSort")
        } catch (e: Throwable) {
            XposedBridge.log("$tag: ShadeListBuilder.dispatchOnBeforeSort skipped: ${e.message}")
        }

        try {
            XposedHelpers.findAndHookMethod(
                shadeBuilder,
                classLoader,
                "sortListAndGroups",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!FocusPinState.shouldPin()) return
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val list = XposedHelpers.getObjectField(param.thisObject, "mNotifList")
                                as? MutableList<Any?> ?: return
                            safePinLyricEntryToFront(list)
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
            XposedBridge.log("$tag: FocusPin hooked ShadeListBuilder.sortListAndGroups")
        } catch (e: Throwable) {
            XposedBridge.log("$tag: ShadeListBuilder.sortListAndGroups skipped: ${e.message}")
        }
    }

    private fun hookFocusedNotifPromptController(classLoader: ClassLoader, tag: String) {
        try {
            val clazz = XposedHelpers.findClass(FOCUSED_NOTIF_CONTROLLER, classLoader)
            val afterHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!FocusPinState.shouldPin()) return
                    runPinSafely {
                        pinControllerLists(param.thisObject)
                        pinAnyListArg(param.args)
                        pinAnyListResult(param.result)
                    }
                }
            }
            val beforeHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!FocusPinState.shouldPin()) return
                    runPinSafely {
                        pinAnyListArg(param.args)
                    }
                }
            }
            var hooked = 0
            for (method in clazz.declaredMethods) {
                if (!shouldHookControllerMethod(method)) continue
                try {
                    val name = method.name.lowercase()
                    if (name.contains("sort") || name.contains("list") || name.contains("bean")) {
                        XposedBridge.hookMethod(method, beforeHook)
                    } else {
                        XposedBridge.hookMethod(method, afterHook)
                    }
                    hooked++
                } catch (_: Throwable) {
                }
            }
            XposedBridge.log("$tag: FocusPin hooked $FOCUSED_NOTIF_CONTROLLER ($hooked methods)")
        } catch (e: Throwable) {
            XposedBridge.log("$tag: FocusedNotifPromptController skipped: ${e.message}")
        }
    }

    private fun hookFocusedNotifPromptView(classLoader: ClassLoader, tag: String) {
        val listHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!FocusPinState.shouldPin()) return
                pinAnyListArg(param.args)
                for (arg in param.args) {
                    if (arg is Array<*>) {
                        @Suppress("UNCHECKED_CAST")
                        pinArrayItems(arg as Array<Any?>)
                    }
                }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                if (!FocusPinState.shouldPin()) return
                val view = param.thisObject as? ViewGroup ?: return
                scheduleRepeatedViewReorder(view)
            }
        }
        try {
            val clazz = XposedHelpers.findClass(FOCUSED_NOTIF_VIEW, classLoader)
            for (methodName in listOf("setData", "bind", "update", "refresh", "show", "onDataChanged")) {
                try {
                    XposedBridge.hookAllMethods(clazz, methodName, listHook)
                } catch (_: Throwable) {
                }
            }
            XposedBridge.log("$tag: FocusPin hooked $FOCUSED_NOTIF_VIEW")
        } catch (e: Throwable) {
            XposedBridge.log("$tag: FocusedNotifPromptView skipped: ${e.message}")
        }
    }

    private fun hookFocusPluginImpl(classLoader: ClassLoader, tag: String) {
        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!FocusPinState.shouldPin()) return
                pinAnyListArg(param.args)
                pinAnyListResult(param.result)
                pinControllerLists(param.thisObject)
            }
        }
        for (className in focusPluginCandidates) {
            try {
                val clazz = classLoader.loadClass(className)
                for (method in clazz.declaredMethods) {
                    val name = method.name.lowercase()
                    if (name.contains("sort") || name.contains("order") ||
                        name.contains("list") || name.contains("focus") ||
                        name.contains("bind") || name.contains("update")
                    ) {
                        try {
                            XposedBridge.hookMethod(method, hook)
                        } catch (_: Throwable) {
                        }
                    }
                }
                XposedBridge.log("$tag: FocusPin hooked focus plugin $className")
            } catch (_: Throwable) {
            }
        }
    }

    private fun hookCountdownFocusCandidates(classLoader: ClassLoader, tag: String) {
        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!FocusPinState.shouldPin()) return
                pinAnyListArg(param.args)
                pinAnyListResult(param.result)
                pinControllerLists(param.thisObject)
                val view = param.thisObject as? ViewGroup
                if (view != null) {
                    view.post { demoteCountdownViewsAboveLyric(view) }
                }
            }
        }
        for (className in countdownCoordinatorCandidates + countdownContainerCandidates) {
            try {
                val clazz = classLoader.loadClass(className)
                XposedBridge.hookAllMethods(clazz, "addView", hook)
                XposedBridge.hookAllMethods(clazz, "setData", hook)
                XposedBridge.hookAllMethods(clazz, "update", hook)
                for (method in clazz.declaredMethods) {
                    val name = method.name.lowercase()
                    if (name.contains("sort") || name.contains("list") ||
                        name.contains("count") || name.contains("timer")
                    ) {
                        try {
                            XposedBridge.hookMethod(method, hook)
                        } catch (_: Throwable) {
                        }
                    }
                }
                XposedBridge.log("$tag: FocusPin hooked countdown focus $className")
            } catch (_: Throwable) {
            }
        }
    }

    private fun hookFocusCoordinatorCandidates(classLoader: ClassLoader, tag: String) {
        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!FocusPinState.shouldPin()) return
                pinAnyListArg(param.args)
                pinAnyListResult(param.result)
                pinControllerLists(param.thisObject)
            }
        }
        for (className in focusCoordinatorCandidates) {
            try {
                val clazz = classLoader.loadClass(className)
                for (method in clazz.declaredMethods) {
                    val name = method.name.lowercase()
                    if (name.contains("sort") || name.contains("order") ||
                        name.contains("list") || name.contains("compare") ||
                        name.contains("update") || name.contains("build")
                    ) {
                        try {
                            XposedBridge.hookMethod(method, hook)
                        } catch (_: Throwable) {
                        }
                    }
                }
                XposedBridge.log("$tag: FocusPin hooked focus coordinator $className")
            } catch (_: Throwable) {
            }
        }
    }

    private fun hookFocusViewContainers(classLoader: ClassLoader, tag: String) {
        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!FocusPinState.shouldPin()) return
                val container = param.thisObject as? ViewGroup ?: return
                container.post { demoteCountdownViewsAboveLyric(container) }
            }
        }
        for (className in focusContainerCandidates) {
            try {
                val clazz = classLoader.loadClass(className)
                if (!ViewGroup::class.java.isAssignableFrom(clazz)) continue
                XposedBridge.hookAllMethods(clazz, "addView", hook)
                XposedBridge.hookAllMethods(clazz, "addViewInLayout", hook)
                XposedBridge.hookAllMethods(clazz, "removeView", hook)
                XposedBridge.hookAllMethods(clazz, "setData", hook)
                XposedBridge.hookAllMethods(clazz, "update", hook)
                XposedBridge.log("$tag: FocusPin hooked focus container $className")
            } catch (_: Throwable) {
            }
        }
    }

    private fun hookNotificationStackScrollLayout(classLoader: ClassLoader, tag: String) {
        try {
            val stackClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.stack.NotificationStackScrollLayout",
                classLoader
            )
            val hook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!FocusPinState.shouldPin()) return
                    val stack = param.thisObject as? ViewGroup ?: return
                    stack.post { demoteCountdownViewsAboveLyric(stack) }
                }
            }
            XposedBridge.hookAllMethods(stackClass, "addView", hook)
            XposedBridge.hookAllMethods(stackClass, "removeView", hook)
            XposedBridge.hookAllMethods(stackClass, "updateNotificationStack", hook)
            XposedBridge.log("$tag: FocusPin hooked NotificationStackScrollLayout")
        } catch (e: Throwable) {
            XposedBridge.log("$tag: NotificationStackScrollLayout pin skipped: ${e.message}")
        }
    }

    fun scheduleViewReorder(root: View?) {
        if (!FocusPinState.shouldPin() || root == null) return
        scheduleRepeatedViewReorder(root)
    }

    private fun scheduleRepeatedViewReorder(root: View) {
        val delays = longArrayOf(0L, 80L, 200L, 500L, 1000L)
        for (delay in delays) {
            root.postDelayed({
                if (!FocusPinState.shouldPin()) return@postDelayed
                if (root is ViewGroup) {
                    demoteCountdownViewsAboveLyric(root)
                }
            }, delay)
        }
    }

    private fun pinArrayItems(array: Array<Any?>) {
        val list = array.filterNotNull().toMutableList<Any?>()
        if (safePinLyricEntryToFront(list)) {
            for (i in array.indices) {
                array[i] = list.getOrNull(i)
            }
        }
    }

    private fun pinControllerLists(controller: Any) {
        val lists = findAllListsInObject(controller)
        val lyricList = lists.firstOrNull { list -> list.any { isLyricListItem(it) } }
        val countdownOnlyList = lists.firstOrNull { list ->
            list.any { isCountdownListItem(it) } && list.none { isLyricListItem(it) }
        }
        if (lyricList != null && countdownOnlyList != null && lyricList !== countdownOnlyList) {
            swapListFieldsIfNeeded(controller, countdownOnlyList, lyricList)
        }
        for (list in lists) {
            safePinLyricEntryToFront(list)
        }
    }

    private fun findAllListsInObject(target: Any): List<MutableList<Any?>> {
        val result = mutableListOf<MutableList<Any?>>()
        for (fieldName in controllerListFields) {
            try {
                @Suppress("UNCHECKED_CAST")
                val list = XposedHelpers.getObjectField(target, fieldName) as? MutableList<Any?>
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

    private fun swapListFieldsIfNeeded(
        controller: Any,
        countdownList: MutableList<Any?>,
        lyricList: MutableList<Any?>
    ) {
        var countdownField: java.lang.reflect.Field? = null
        var lyricField: java.lang.reflect.Field? = null
        for (field in controller.javaClass.declaredFields) {
            if (!MutableList::class.java.isAssignableFrom(field.type)) continue
            try {
                field.isAccessible = true
                when (field.get(controller)) {
                    countdownList -> countdownField = field
                    lyricList -> lyricField = field
                }
            } catch (_: Throwable) {
            }
        }
        if (countdownField != null && lyricField != null) {
            val countdownName = countdownField.name.lowercase()
            if (countdownName.contains("count") || countdownName.contains("timer") ||
                countdownName.contains("top") || countdownName.contains("priority")
            ) {
                try {
                    val temp = countdownField.get(controller)
                    countdownField.set(controller, lyricField.get(controller))
                    lyricField.set(controller, temp)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun pinAnyListArg(args: Array<Any?>) {
        for (arg in args) {
            @Suppress("UNCHECKED_CAST")
            val list = arg as? MutableList<Any?> ?: continue
            safePinLyricEntryToFront(list)
        }
    }

    private fun pinAnyListResult(result: Any?) {
        @Suppress("UNCHECKED_CAST")
        val list = result as? MutableList<Any?> ?: return
        safePinLyricEntryToFront(list)
    }

    private fun safePinLyricEntryToFront(list: MutableList<Any?>): Boolean {
        return try {
            pinLyricEntryToFront(list)
        } catch (_: UnsupportedOperationException) {
            false
        } catch (_: ConcurrentModificationException) {
            false
        }
    }

    private fun pinLyricEntryToFront(list: MutableList<Any?>): Boolean {
        if (list.isEmpty()) return false
        val lyricIndex = list.indexOfFirst { isLyricListItem(it) }
        if (lyricIndex < 0) return false
        var changed = false
        if (lyricIndex > 0) {
            val lyric = list.removeAt(lyricIndex)
            list.add(0, lyric)
            changed = true
        }
        // 倒计时类焦点通知有系统级优先权，强制排到歌词之后
        val countdownItems = list.filterIndexed { index, item ->
            index > 0 && isCountdownListItem(item)
        }
        if (countdownItems.isNotEmpty()) {
            for (item in countdownItems) {
                list.remove(item)
            }
            val insertAt = list.indexOfFirst { isLyricListItem(it) }.let { if (it >= 0) it + 1 else 1 }
            list.addAll(insertAt.coerceAtMost(list.size), countdownItems)
            changed = true
        }
        return changed
    }

    private fun isLyricListItem(item: Any?): Boolean {
        if (item == null) return false
        extractStatusBarNotification(item)?.let { return isLyricFocusSbn(it) }
        return isLyricNotifKey(item)
    }

    private fun isLyricNotifKey(item: Any): Boolean {
        for (fieldName in listOf("mKey", "key", "mNotificationKey", "notificationKey")) {
            try {
                val key = XposedHelpers.getObjectField(item, fieldName)?.toString() ?: continue
                if (key.contains(HyperFocusLyricStyle.CHANNEL_ID) ||
                    key.contains("focusNotifLyrics")
                ) {
                    return true
                }
            } catch (_: Throwable) {
            }
        }
        try {
            val id = XposedHelpers.getIntField(item, "mId")
            if (id == HyperFocusLyricStyle.CHANNEL_ID.hashCode()) return true
        } catch (_: Throwable) {
        }
        try {
            val id = XposedHelpers.getIntField(item, "id")
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

    private fun isCountdownListItem(item: Any?): Boolean {
        if (item == null || isLyricListItem(item)) return false
        extractStatusBarNotification(item)?.let { return isCountdownFocusSbn(it) }
        return isCountdownNotifKey(item)
    }

    private fun isCountdownNotifKey(item: Any): Boolean {
        for (fieldName in listOf("mType", "type", "mScene", "scene", "mBusiness", "business")) {
            try {
                val value = XposedHelpers.getObjectField(item, fieldName)?.toString()?.lowercase()
                    ?: continue
                if (value.contains("count") || value.contains("timer") || value.contains("倒计时")) {
                    return true
                }
            } catch (_: Throwable) {
            }
        }
        return false
    }

    private fun isCountdownFocusSbn(sbn: StatusBarNotification): Boolean {
        if (isLyricFocusSbn(sbn)) return false
        val notification = sbn.notification ?: return false
        val extras = notification.extras
        if (extras.getBoolean("android.showChronometer", false)) return true
        if (extras.containsKey("android.chronometerCountDown")) return true
        val focusParam = extras.getString("miui.focus.param.custom")
            ?: extras.getString("miui.focus.param")
            ?: extras.getString("miui.focus.params")
        if (focusParam != null) {
            val lower = focusParam.lowercase()
            if (lower.contains("countdown") || lower.contains("count_down") ||
                lower.contains("\"business\":\"timer\"") ||
                lower.contains("\"business\":\"countdown\"") ||
                lower.contains("倒计时")
            ) {
                return true
            }
        }
        val pkg = sbn.packageName.lowercase()
        return pkg.contains("deskclock") || pkg.contains("timer") || pkg.contains("clock")
    }

    private fun extractStatusBarNotification(item: Any): StatusBarNotification? {
        if (item is StatusBarNotification) return item
        try {
            val direct = XposedHelpers.callMethod(item, "getSbn") as? StatusBarNotification
            if (direct != null) return direct
        } catch (_: Throwable) {
        }
        try {
            val entry = XposedHelpers.callMethod(item, "getEntry") ?: return null
            return XposedHelpers.getObjectField(entry, "mSbn") as? StatusBarNotification
        } catch (_: Throwable) {
        }
        for (fieldName in listOf("mEntry", "entry", "mNotificationEntry")) {
            try {
                val entry = XposedHelpers.getObjectField(item, fieldName) ?: continue
                val sbn = XposedHelpers.getObjectField(entry, "mSbn") as? StatusBarNotification
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

    private fun demoteCountdownViewsAboveLyric(container: ViewGroup) {
        try {
            moveLyricViewToTop(container)
            val lyricView = findLyricFocusView(container) ?: return
            for (countdownView in findCountdownFocusViews(container)) {
                val lyricParent = lyricView.parent as? ViewGroup ?: continue
                val countdownParent = countdownView.parent as? ViewGroup ?: continue
                if (lyricParent === countdownParent) {
                    val lyricIndex = lyricParent.indexOfChild(lyricView)
                    val countdownIndex = countdownParent.indexOfChild(countdownView)
                    if (countdownIndex < lyricIndex) {
                        countdownParent.removeView(countdownView)
                        countdownParent.addView(countdownView, lyricIndex)
                    }
                    continue
                }
                val lyricSection = findSectionRoot(lyricView, container)
                val countdownSection = findSectionRoot(countdownView, container)
                if (lyricSection == null || countdownSection == null || lyricSection === countdownSection) {
                    continue
                }
                val sectionParent = lyricSection.parent as? ViewGroup ?: continue
                val lyricSectionIndex = sectionParent.indexOfChild(lyricSection)
                val countdownSectionIndex = sectionParent.indexOfChild(countdownSection)
                if (countdownSectionIndex < lyricSectionIndex) {
                    sectionParent.removeView(lyricSection)
                    sectionParent.addView(lyricSection, countdownSectionIndex)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun findSectionRoot(view: View, root: ViewGroup): View? {
        var current: View = view
        while (current.parent is ViewGroup && current.parent !== root) {
            current = current.parent as ViewGroup
        }
        return if (current.parent === root) current else null
    }

    private fun findCountdownFocusViews(container: ViewGroup): List<View> {
        val result = mutableListOf<View>()
        collectCountdownViews(container, result)
        return result
    }

    private fun collectCountdownViews(group: ViewGroup, out: MutableList<View>) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (isCountdownFocusView(child)) {
                out.add(child)
            }
            if (child is ViewGroup) {
                collectCountdownViews(child, out)
            }
        }
    }

    private fun isCountdownFocusView(view: View): Boolean {
        if (isLyricFocusView(view)) return false
        val className = view.javaClass.name.lowercase()
        if (className.contains("countdown") || className.contains("count_down") ||
            (className.contains("timer") && className.contains("focus"))
        ) {
            return true
        }
        extractSbnFromView(view)?.let { if (isCountdownFocusSbn(it)) return true }
        return view is ViewGroup && hasChronometerChild(view)
    }

    private fun isLyricFocusView(view: View): Boolean {
        extractSbnFromView(view)?.let { return isLyricFocusSbn(it) }
        return false
    }

    private fun hasChronometerChild(group: ViewGroup): Boolean {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child.javaClass.name.contains("Chronometer")) return true
            if (child is ViewGroup && hasChronometerChild(child)) return true
        }
        return false
    }

    private fun moveLyricViewToTop(container: ViewGroup) {
        try {
            val lyricView = findLyricFocusView(container) ?: return
            var current: View = lyricView
            while (true) {
                val parent = current.parent as? ViewGroup ?: break
                val index = parent.indexOfChild(current)
                if (index > 0) {
                    parent.removeView(current)
                    parent.addView(current, 0)
                }
                if (parent === container) break
                current = parent
            }
        } catch (_: Throwable) {
        }
    }

    private fun reorderLyricViewsToTop(container: ViewGroup) {
        moveLyricViewToTop(container)
    }

    private fun findLyricFocusView(container: ViewGroup): View? {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val sbn = extractSbnFromView(child)
            if (sbn != null && isLyricFocusSbn(sbn)) {
                return child
            }
            if (child is ViewGroup) {
                findLyricFocusView(child)?.let { return it }
            }
        }
        return null
    }

    private fun extractSbnFromView(view: View): StatusBarNotification? {
        return try {
            val entry = XposedHelpers.callMethod(view, "getEntry") ?: return null
            XposedHelpers.getObjectField(entry, "mSbn") as? StatusBarNotification
        } catch (_: Throwable) {
            null
        }
    }
}
