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
 * 在焦点通知数据层 / 列表层将歌词焦点通知置顶（高于其他焦点通知与 HyperIsland 转换通知）。
 * 仅操作 NotificationStackScrollLayout 子 View 顺序无法影响锁屏焦点卡片排序。
 */
object FocusPinAboveHook {

    private val pinGuard = ThreadLocal.withInitial { false }

    /** 防止递归重入 ShadeListBuilder buildList 导致 section 重复崩溃 */
    private val buildListGuard = ThreadLocal.withInitial { false }

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
        "mLiveNotifBeans",
        "mSortListEntries",
        "mPendingList"
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

    fun install(classLoader: ClassLoader, module: XposedModule, tag: String) {
        hookShadeListBuilder(classLoader, module, tag)
        hookFocusedNotifPromptController(classLoader, module, tag)
        hookFocusedNotifPromptView(classLoader, module, tag)
        hookFocusCoordinatorCandidates(classLoader, module, tag)
        hookFocusViewContainers(classLoader, module, tag)
        hookNotificationStackScrollLayout(classLoader, module, tag)
        hookFocusPluginImpl(classLoader, module, tag)
        hookCountdownFocusCandidates(classLoader, module, tag)
        hookFocusComparator(classLoader, module, tag)
        hookNotificationPanelReflow(classLoader, module, tag)
        hookNotifEntryChangeListeners(classLoader, module, tag)
    }

    private var topLevelComparatorHooked = false

    private fun hookFocusComparator(classLoader: ClassLoader, module: XposedModule, tag: String) {
        try {
            val clazz = ReflectUtil.findClass(FOCUSED_NOTIF_CONTROLLER, classLoader)
            for (inner in clazz.declaredClasses) {
                if (!Comparator::class.java.isAssignableFrom(inner)) continue
                for (method in inner.declaredMethods) {
                    if (method.name != "compare") continue
                    module.hook(method).intercept { chain ->
                        if (!FocusPinState.shouldPin()) return@intercept chain.proceed()
                        val left = chain.args.getOrNull(0)
                        val right = chain.args.getOrNull(1)
                        val leftLyric = isLyricListItem(left)
                        val rightLyric = isLyricListItem(right)
                        when {
                            leftLyric && !rightLyric -> return@intercept -1
                            !leftLyric && rightLyric -> return@intercept 1
                            leftLyric && isCountdownListItem(right) -> return@intercept -1
                            isCountdownListItem(left) && rightLyric -> return@intercept 1
                        }
                        chain.proceed()
                    }
                }
                module.log(Log.INFO, tag, "FocusPin hooked comparator ${inner.name}")
            }
        } catch (e: Throwable) {
            module.log(Log.INFO, tag, "Focus comparator hook skipped: ${e.message}")
        }
        // Also hook ShadeListBuilder.mTopLevelComparator which is the REAL comparator used by sortList
        hookTopLevelComparator(classLoader, module, tag)
    }

    private fun hookTopLevelComparator(classLoader: ClassLoader, module: XposedModule, tag: String) {
        try {
            val controllerClazz = ReflectUtil.findClass(FOCUSED_NOTIF_CONTROLLER, classLoader)
            // Hook any method that gives us access to the controller instance so we can chain to ShadeListBuilder
            for (method in controllerClazz.declaredMethods) {
                if (Modifier.isStatic(method.modifiers)) continue
                try {
                    module.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        if (topLevelComparatorHooked) return@intercept result
                        try {
                            val controller = chain.thisObject
                            val focusCoordinator = ReflectUtil.getField(controller, "mFocusCoordinator")
                            val pipeline = ReflectUtil.getField(focusCoordinator!!, "mPipeline")
                            val shadeListBuilder = ReflectUtil.getField(pipeline!!, "mShadeListBuilder")
                            val comparator = ReflectUtil.getField(shadeListBuilder!!, "mTopLevelComparator") as? Comparator<*>
                            if (comparator != null) {
                                val compareMethod = comparator.javaClass.getDeclaredMethod("compare", Object::class.java, Object::class.java)
                                compareMethod.isAccessible = true
                                module.hook(compareMethod).intercept { compChain ->
                                    if (!FocusPinState.shouldPin()) return@intercept compChain.proceed()
                                    val left = compChain.args.getOrNull(0)
                                    val right = compChain.args.getOrNull(1)
                                    val lk = isLyricListItem(left)
                                    val rk = isLyricListItem(right)
                                    when {
                                        lk && !rk -> return@intercept -1
                                        !lk && rk -> return@intercept 1
                                        lk && isCountdownListItem(right) -> return@intercept -1
                                        isCountdownListItem(left) && rk -> return@intercept 1
                                    }
                                    compChain.proceed()
                                }
                                topLevelComparatorHooked = true
                                module.log(Log.INFO, tag, "FocusPin hooked ShadeListBuilder.mTopLevelComparator")
                            }
                        } catch (_: Throwable) {}
                        result
                    }
                } catch (_: Throwable) {}
            }
        } catch (e: Throwable) {
            module.log(Log.INFO, tag, "TopLevelComparator hook skipped: ${e.message}")
        }
    }

    private fun hookNotificationPanelReflow(classLoader: ClassLoader, module: XposedModule, tag: String) {
        val targets = listOf(
            "com.android.systemui.shade.MiuiNotificationPanelViewController",
            "com.android.systemui.shade.NotificationPanelViewController"
        )
        for (target in targets) {
            try {
                val method = ReflectUtil.findMethod(target, classLoader, "positionClockAndNotifications", Boolean::class.java)
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (!FocusPinState.shouldPin()) return@intercept result
                    try {
                        val view = ReflectUtil.getField(chain.thisObject, "mView") as? View
                            ?: ReflectUtil.getField(chain.thisObject, "mNotificationContainerParent") as? View
                        if (view is ViewGroup) {
                            view.post { demoteCountdownViewsAboveLyric(view) }
                        }
                    } catch (_: Throwable) {
                    }
                    result
                }
                module.log(Log.INFO, tag, "FocusPin hooked panel reflow on $target")
                break
            } catch (_: Throwable) {
            }
        }
    }

    private fun hookNotifEntryChangeListeners(classLoader: ClassLoader, module: XposedModule, tag: String) {
        val notifCollectionCandidates = listOf(
            "com.android.systemui.statusbar.notification.collection.NotifCollection",
            "com.android.systemui.statusbar.notification.NotifCollection"
        )
        for (className in notifCollectionCandidates) {
            try {
                val clazz = classLoader.loadClass(className)
                for (methodName in listOf("addEntry", "removeEntry", "updateEntry",
                    "onEntryAdded", "onEntryUpdated", "onEntryRemoved"))
                {
                    val methods = ReflectUtil.findMethodsByName(clazz, methodName)
                    for (method in methods) {
                        try {
                            module.hook(method).intercept { chain ->
                                val result = chain.proceed()
                                if (FocusPinState.shouldPin()) {
                                    runPinSafely {
                                        pinAnyListArg(chain.args.toTypedArray())
                                        pinAnyListResult(result)
                                        pinControllerLists(chain.thisObject)
                                    }
                                }
                                result
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                for (method in clazz.declaredMethods) {
                    if (Modifier.isStatic(method.modifiers)) continue
                    val name = method.name.lowercase()
                    if ((name.contains("add") && name.contains("entry")) ||
                        (name.contains("remove") && name.contains("entry")) ||
                        (name.contains("update") && name.contains("entry")) ||
                        name.contains("onentry") ||
                        name.contains("notifchange")
                    ) {
                        try {
                            module.hook(method).intercept { chain ->
                                val result = chain.proceed()
                                if (FocusPinState.shouldPin()) {
                                    runPinSafely {
                                        pinAnyListArg(chain.args.toTypedArray())
                                        pinAnyListResult(result)
                                        pinControllerLists(chain.thisObject)
                                    }
                                }
                                result
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                module.log(Log.INFO, tag, "FocusPin hooked NotifCollection $className")
            } catch (_: Throwable) {
            }
        }
        val pipelineCandidates = listOf(
            "com.android.systemui.statusbar.notification.collection.NotifPipeline",
            "com.android.systemui.statusbar.notification.NotificationEntryManager",
            "com.android.systemui.statusbar.notification.collection.listbuilder.NotifListBuilder"
        )
        for (className in pipelineCandidates) {
            try {
                val clazz = classLoader.loadClass(className)
                for (methodName in listOf("onBeforeRenderEntry", "onEntryAdded", "onEntryUpdated",
                    "onEntryRemoved", "addEntry", "removeEntry", "updateEntry", "renderPipeline"))
                {
                    val methods = ReflectUtil.findMethodsByName(clazz, methodName)
                    for (method in methods) {
                        try {
                            module.hook(method).intercept { chain ->
                                val result = chain.proceed()
                                if (FocusPinState.shouldPin()) {
                                    runPinSafely {
                                        pinAnyListArg(chain.args.toTypedArray())
                                        pinAnyListResult(result)
                                        pinControllerLists(chain.thisObject)
                                    }
                                }
                                result
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                for (method in clazz.declaredMethods) {
                    if (Modifier.isStatic(method.modifiers)) continue
                    val name = method.name.lowercase()
                    if (name.contains("entry") || (
                            name.contains("notif") && (
                                name.contains("add") || name.contains("remove") ||
                                name.contains("update") || name.contains("change"))
                            ))
                    {
                        try {
                            module.hook(method).intercept { chain ->
                                val result = chain.proceed()
                                if (FocusPinState.shouldPin()) {
                                    runPinSafely {
                                        pinAnyListArg(chain.args.toTypedArray())
                                        pinAnyListResult(result)
                                        pinControllerLists(chain.thisObject)
                                    }
                                }
                                result
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                module.log(Log.INFO, tag, "FocusPin hooked pipeline $className")
            } catch (_: Throwable) {
            }
        }
    }

    private fun hookShadeListBuilder(classLoader: ClassLoader, module: XposedModule, tag: String) {
        val shadeBuilder = "com.android.systemui.statusbar.notification.collection.ShadeListBuilder"
        try {
            val dispatchMethod = ReflectUtil.findMethod(shadeBuilder, classLoader, "dispatchOnBeforeSort", List::class.java)
            module.hook(dispatchMethod).intercept { chain ->
                if (buildListGuard.get()) return@intercept chain.proceed()
                if (FocusPinState.shouldPin()) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val list = chain.args.getOrNull(0) as? MutableList<Any?>
                        if (list != null) safePinLyricEntryToFront(list)
                    } catch (_: Throwable) {}
                }
                buildListGuard.set(true)
                val result = try { chain.proceed() } finally { buildListGuard.set(false) }
                if (FocusPinState.shouldPin()) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val list = chain.args.getOrNull(0) as? MutableList<Any?>
                        if (list != null) safePinLyricEntryToFront(list)
                    } catch (_: Throwable) {}
                }
                result
            }
            module.log(Log.INFO, tag, "FocusPin hooked ShadeListBuilder.dispatchOnBeforeSort")
        } catch (e: Throwable) {
            module.log(Log.INFO, tag, "ShadeListBuilder.dispatchOnBeforeSort skipped: ${e.message}")
        }

        try {
            val clazz = ReflectUtil.findClass(shadeBuilder, classLoader)
            val sortMethods = ReflectUtil.findMethodsByName(clazz, "sortListAndGroups")
            for (method in sortMethods) {
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (!FocusPinState.shouldPin()) return@intercept result
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val list = ReflectUtil.getField(chain.thisObject, "mNotifList")
                            as? MutableList<Any?>
                        if (list != null) {
                            safePinLyricEntryToFront(list)
                        }
                    } catch (_: Throwable) {
                    }
                    result
                }
            }
            for (methodName in listOf("onEntryAdded", "onEntryUpdated", "onEntryRemoved")) {
                val entryMethods = ReflectUtil.findMethodsByName(clazz, methodName)
                for (method in entryMethods) {
                    try {
                        module.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            if (FocusPinState.shouldPin()) {
                                runPinSafely {
                                    pinAnyListArg(chain.args.toTypedArray())
                                    pinAnyListResult(result)
                                    try {
                                        @Suppress("UNCHECKED_CAST")
                                        val list = ReflectUtil.getField(chain.thisObject, "mNotifList")
                                            as? MutableList<Any?>
                                        if (list != null) {
                                            safePinLyricEntryToFront(list)
                                        }
                                    } catch (_: Throwable) {
                                    }
                                }
                            }
                            result
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
            for (method in clazz.declaredMethods) {
                if (Modifier.isStatic(method.modifiers)) continue
                val name = method.name.lowercase()
                if (name.contains("entry") || name.contains("notif") && (
                            name.contains("change") || name.contains("added") || name.contains("removed"))
                ) {
                    try {
                        module.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            if (FocusPinState.shouldPin()) {
                                runPinSafely {
                                    pinAnyListArg(chain.args.toTypedArray())
                                    pinAnyListResult(result)
                                    try {
                                        @Suppress("UNCHECKED_CAST")
                                        val list = ReflectUtil.getField(chain.thisObject, "mNotifList")
                                            as? MutableList<Any?>
                                        if (list != null) {
                                            safePinLyricEntryToFront(list)
                                        }
                                    } catch (_: Throwable) {
                                    }
                                }
                            }
                            result
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
            module.log(Log.INFO, tag, "FocusPin hooked ShadeListBuilder.sortListAndGroups")
        } catch (e: Throwable) {
            module.log(Log.INFO, tag, "ShadeListBuilder.sortListAndGroups skipped: ${e.message}")
        }
    }

    private fun hookFocusedNotifPromptController(classLoader: ClassLoader, module: XposedModule, tag: String) {
        try {
            val clazz = ReflectUtil.findClass(FOCUSED_NOTIF_CONTROLLER, classLoader)
            var hooked = 0
            for (method in clazz.declaredMethods) {
                if (!shouldHookControllerMethod(method)) continue
                try {
                    val name = method.name.lowercase()
                    if (name.contains("sort") || name.contains("list") || name.contains("bean")) {
                        module.hook(method).intercept { chain ->
                            if (FocusPinState.shouldPin()) {
                                runPinSafely {
                                    pinAnyListArg(chain.args.toTypedArray())
                                }
                            }
                            chain.proceed()
                        }
                    } else {
                        module.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            if (FocusPinState.shouldPin()) {
                                runPinSafely {
                                    pinControllerLists(chain.thisObject)
                                    pinAnyListArg(chain.args.toTypedArray())
                                    pinAnyListResult(result)
                                }
                            }
                            result
                        }
                    }
                    hooked++
                } catch (_: Throwable) {
                }
            }
            module.log(Log.INFO, tag, "FocusPin hooked $FOCUSED_NOTIF_CONTROLLER ($hooked methods)")
        } catch (e: Throwable) {
            module.log(Log.INFO, tag, "FocusedNotifPromptController skipped: ${e.message}")
        }
        // sortList 是关键的排序入口，必须在排序后重新 pin
        hookSortList(classLoader, module, tag)
    }

    private fun hookSortList(classLoader: ClassLoader, module: XposedModule, tag: String) {
        try {
            val clazz = ReflectUtil.findClass(FOCUSED_NOTIF_CONTROLLER, classLoader)
            val methods = ReflectUtil.findMethodsByName(clazz, "sortList")
            for (method in methods) {
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (FocusPinState.shouldPin()) {
                        runPinSafely {
                            pinControllerLists(chain.thisObject)
                            pinAnyListArg(chain.args.toTypedArray())
                            pinAnyListResult(result)
                        }
                    }
                    result
                }
            }
            module.log(Log.INFO, tag, "FocusPin hooked sortList")
        } catch (e: Throwable) {
            module.log(Log.INFO, tag, "sortList hook skipped: ${e.message}")
        }
    }

    private fun hookFocusedNotifPromptView(classLoader: ClassLoader, module: XposedModule, tag: String) {
        try {
            val clazz = ReflectUtil.findClass(FOCUSED_NOTIF_VIEW, classLoader)
            for (methodName in listOf("setData", "bind", "update", "refresh", "show", "onDataChanged")) {
                try {
                    val methods = ReflectUtil.findMethodsByName(clazz, methodName)
                    for (method in methods) {
                        module.hook(method).intercept { chain ->
                            if (FocusPinState.shouldPin()) {
                                pinAnyListArg(chain.args.toTypedArray())
                                for (arg in chain.args) {
                                    if (arg is Array<*>) {
                                        @Suppress("UNCHECKED_CAST")
                                        pinArrayItems(arg as Array<Any?>)
                                    }
                                }
                            }
                            val result = chain.proceed()
                            if (FocusPinState.shouldPin()) {
                                pinAnyListArg(chain.args.toTypedArray())
                                for (arg in chain.args) {
                                    if (arg is Array<*>) {
                                        @Suppress("UNCHECKED_CAST")
                                        pinArrayItems(arg as Array<Any?>)
                                    }
                                }
                                val view = chain.thisObject as? ViewGroup ?: return@intercept result
                                scheduleRepeatedViewReorder(view)
                            }
                            result
                        }
                    }
                } catch (_: Throwable) {
                }
            }
            module.log(Log.INFO, tag, "FocusPin hooked $FOCUSED_NOTIF_VIEW")
        } catch (e: Throwable) {
            module.log(Log.INFO, tag, "FocusedNotifPromptView skipped: ${e.message}")
        }
    }

    private fun hookFocusPluginImpl(classLoader: ClassLoader, module: XposedModule, tag: String) {
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
                            module.hook(method).intercept { chain ->
                                val result = chain.proceed()
                                if (FocusPinState.shouldPin()) {
                                    pinAnyListArg(chain.args.toTypedArray())
                                    pinAnyListResult(result)
                                    pinControllerLists(chain.thisObject)
                                }
                                result
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                module.log(Log.INFO, tag, "FocusPin hooked focus plugin $className")
            } catch (_: Throwable) {
            }
        }
    }

    private fun hookCountdownFocusCandidates(classLoader: ClassLoader, module: XposedModule, tag: String) {
        for (className in countdownCoordinatorCandidates + countdownContainerCandidates) {
            try {
                val clazz = classLoader.loadClass(className)
                for (hookName in listOf("addView", "setData", "update")) {
                    val methods = ReflectUtil.findMethodsByName(clazz, hookName)
                    for (method in methods) {
                        try {
                            module.hook(method).intercept { chain ->
                                val result = chain.proceed()
                                if (FocusPinState.shouldPin()) {
                                    pinAnyListArg(chain.args.toTypedArray())
                                    pinAnyListResult(result)
                                    pinControllerLists(chain.thisObject)
                                    val view = chain.thisObject as? ViewGroup
                                    if (view != null) {
                                        view.post { demoteCountdownViewsAboveLyric(view) }
                                    }
                                }
                                result
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                for (method in clazz.declaredMethods) {
                    val name = method.name.lowercase()
                    if (name.contains("sort") || name.contains("list") ||
                        name.contains("count") || name.contains("timer")
                    ) {
                        try {
                            module.hook(method).intercept { chain ->
                                val result = chain.proceed()
                                if (FocusPinState.shouldPin()) {
                                    pinAnyListArg(chain.args.toTypedArray())
                                    pinAnyListResult(result)
                                    pinControllerLists(chain.thisObject)
                                    val view = chain.thisObject as? ViewGroup
                                    if (view != null) {
                                        view.post { demoteCountdownViewsAboveLyric(view) }
                                    }
                                }
                                result
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                module.log(Log.INFO, tag, "FocusPin hooked countdown focus $className")
            } catch (_: Throwable) {
            }
        }
    }

    private fun hookFocusCoordinatorCandidates(classLoader: ClassLoader, module: XposedModule, tag: String) {
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
                            module.hook(method).intercept { chain ->
                                val result = chain.proceed()
                                if (FocusPinState.shouldPin()) {
                                    pinAnyListArg(chain.args.toTypedArray())
                                    pinAnyListResult(result)
                                    pinControllerLists(chain.thisObject)
                                }
                                result
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                module.log(Log.INFO, tag, "FocusPin hooked focus coordinator $className")
            } catch (_: Throwable) {
            }
        }
    }

    private fun hookFocusViewContainers(classLoader: ClassLoader, module: XposedModule, tag: String) {
        for (className in focusContainerCandidates) {
            try {
                val clazz = classLoader.loadClass(className)
                if (!ViewGroup::class.java.isAssignableFrom(clazz)) continue
                for (hookName in listOf("addView", "addViewInLayout", "removeView", "setData", "update")) {
                    val methods = ReflectUtil.findMethodsByName(clazz, hookName)
                    for (method in methods) {
                        try {
                            module.hook(method).intercept { chain ->
                                val result = chain.proceed()
                                if (FocusPinState.shouldPin()) {
                                    val container = chain.thisObject as? ViewGroup
                                    if (container != null) {
                                        container.post { demoteCountdownViewsAboveLyric(container) }
                                    }
                                }
                                result
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                module.log(Log.INFO, tag, "FocusPin hooked focus container $className")
            } catch (_: Throwable) {
            }
        }
    }

    private fun hookNotificationStackScrollLayout(classLoader: ClassLoader, module: XposedModule, tag: String) {
        try {
            val stackClass = ReflectUtil.findClass(
                "com.android.systemui.statusbar.stack.NotificationStackScrollLayout",
                classLoader
            )
            for (hookName in listOf("addView", "removeView", "updateNotificationStack")) {
                val methods = ReflectUtil.findMethodsByName(stackClass, hookName)
                for (method in methods) {
                    try {
                        module.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            if (FocusPinState.shouldPin()) {
                                val stack = chain.thisObject as? ViewGroup
                                if (stack != null) {
                                    stack.post { demoteCountdownViewsAboveLyric(stack) }
                                }
                            }
                            result
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
            module.log(Log.INFO, tag, "FocusPin hooked NotificationStackScrollLayout")
        } catch (e: Throwable) {
            module.log(Log.INFO, tag, "NotificationStackScrollLayout pin skipped: ${e.message}")
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
        if (pinGuard.get()) return false
        pinGuard.set(true)
        return try {
            pinLyricEntryToFront(list)
        } catch (_: UnsupportedOperationException) {
            false
        } catch (_: ConcurrentModificationException) {
            false
        } finally {
            pinGuard.set(false)
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
        // 所有非歌词条目强制排到歌词之后
        val nonLyricAfter = ArrayList<Any?>()
        val iter = list.listIterator(1)
        while (iter.hasNext()) {
            val item = iter.next()
            if (!isLyricListItem(item) && !isCountdownListItem(item)) {
                nonLyricAfter.add(item)
                iter.remove()
                changed = true
            }
        }
        if (nonLyricAfter.isNotEmpty()) {
            list.addAll(nonLyricAfter)
        }
        // 倒计时类焦点通知强制排到非歌词条目之后
        val countdownItems = list.filterIndexed { index, item ->
            index > 0 && isCountdownListItem(item)
        }
        if (countdownItems.isNotEmpty()) {
            for (item in countdownItems) {
                list.remove(item)
            }
            list.addAll(countdownItems)
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

    private fun isCountdownListItem(item: Any?): Boolean {
        if (item == null || isLyricListItem(item)) return false
        extractStatusBarNotification(item)?.let { return isCountdownFocusSbn(it) }
        return isCountdownNotifKey(item)
    }

    private fun isCountdownNotifKey(item: Any): Boolean {
        for (fieldName in listOf("mType", "type", "mScene", "scene", "mBusiness", "business")) {
            try {
                val value = ReflectUtil.getField(item, fieldName)?.toString()?.lowercase()
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
            val entry = ReflectUtil.callMethod(view, "getEntry") ?: return null
            ReflectUtil.getField(entry, "mSbn") as? StatusBarNotification
        } catch (_: Throwable) {
            null
        }
    }
}
