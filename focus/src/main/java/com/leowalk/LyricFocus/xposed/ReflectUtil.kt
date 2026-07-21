package com.leowalk.LyricFocus.xposed

import android.util.Log
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Minimal reflection utilities replacing legacy XposedHelpers for Modern API migration.
 * All methods work on raw ClassLoader-based reflection without framework dependencies.
 */
object ReflectUtil {

    fun findClass(className: String, classLoader: ClassLoader): Class<*> {
        return classLoader.loadClass(className)
    }

    fun findMethod(className: String, classLoader: ClassLoader, methodName: String, vararg paramTypes: Class<*>): Method {
        val clazz = findClass(className, classLoader)
        val method = clazz.getDeclaredMethod(methodName, *paramTypes)
        method.isAccessible = true
        return method
    }

    fun findMethodsByName(clazz: Class<*>, methodName: String): List<Method> {
        return clazz.declaredMethods.filter { it.name == methodName }
    }

    fun getField(obj: Any, fieldName: String): Any? {
        var current: Class<*>? = obj.javaClass
        while (current != null) {
            try {
                val field = current.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(obj)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    fun getStaticField(clazz: Class<*>, fieldName: String): Any? {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            return field.get(null)
        } catch (_: Throwable) {
            return null
        }
    }

    fun callMethod(obj: Any, methodName: String, vararg args: Any?): Any? {
        var current: Class<*>? = obj.javaClass
        while (current != null) {
            try {
                val method = current.declaredMethods.firstOrNull { m ->
                    m.name == methodName
                } ?: throw NoSuchMethodException(methodName)
                method.isAccessible = true
                return method.invoke(obj, *args)
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        throw NoSuchMethodException("$methodName not found in ${obj.javaClass.name}")
    }

    fun logE(tag: String, msg: String, throwable: Throwable? = null) {
        Log.e(tag, msg, throwable)
    }
}
