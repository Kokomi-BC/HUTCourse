package cn.edu.hut.course.glass

import android.app.Activity
import android.util.Log
import android.view.View

/**
 * 在 Activity 的 DecorView 上注入 ComposeView 所需的 ViewTreeOwners。
 * 使用反射兼容不同 lifecycle/savedstate 版本（直接导入会被 Compose 库的依赖版本覆盖）。
 */
object LifecycleInjector {

    private var injected = false
    private var standaloneOwner: StandaloneOwner? = null

    @JvmStatic
    fun inject(activity: Activity) {
        if (injected) return
        val decorView = activity.window?.decorView ?: return

        // 1. LifecycleOwner — FlutterActivity 自身实现了 LifecycleOwner
        callStaticSet(
            "androidx.lifecycle.ViewTreeLifecycleOwner", "set",
            decorView, activity
        )
        Log.d("LifecycleInjector", "LifecycleOwner injected")

        // 2. SavedStateRegistryOwner — FlutterActivity 不实现，用 StandaloneOwner
        val savedOwner = StandaloneOwner().also { it.onCreate() }
        standaloneOwner = savedOwner
        callStaticSet(
            "androidx.savedstate.ViewTreeSavedStateRegistryOwner", "set",
            decorView, savedOwner
        )
        Log.d("LifecycleInjector", "SavedStateRegistryOwner injected")

        injected = true
    }

    private fun callStaticSet(className: String, methodName: String, view: View, owner: Any) {
        try {
            val cls = Class.forName(className)
            val method = cls.declaredMethods.firstOrNull {
                it.name == methodName && it.parameterCount == 2
            }
            method?.apply {
                isAccessible = true
                invoke(null, view, owner)
            }
        } catch (e: Exception) {
            Log.e("LifecycleInjector", "$className.$methodName failed: ${e.message}")
            // Fallback: setTag
            val tagName = when {
                className.contains("lifecycle") -> "view_tree_lifecycle_owner"
                className.contains("savedstate") -> "view_tree_saved_state_registry_owner"
                else -> return
            }
            for (pkg in listOf("androidx.lifecycle", "androidx.savedstate", view.context.packageName)) {
                val id = view.resources.getIdentifier(tagName, "id", pkg)
                if (id != 0) {
                    view.setTag(id, owner)
                    Log.d("LifecycleInjector", "setTag fallback: $tagName id=$id")
                    return
                }
            }
            Log.e("LifecycleInjector", "All fallbacks failed for $tagName")
        }
    }
}
