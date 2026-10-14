package com.blackcat.remote

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.WeakHashMap

/** One explicitly checked key for a later explicit request. No disk storage or requests. */
internal object PendingApiKey {
    private val keys = WeakHashMap<Activity, String>()
    private val applications = WeakHashMap<Application, Boolean>()

    @Synchronized fun put(activity: Activity, key: String) {
        require(OpenAiPlanner.validKey(key)) { "Invalid key format" }
        if (activity.isDestroyed || activity.isFinishing) return
        val application = activity.application
        if (applications.put(application, true) == null) {
            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityDestroyed(a: Activity) { clear(a) }
                override fun onActivityCreated(a: Activity, state: Bundle?) {}
                override fun onActivityStarted(a: Activity) {}
                override fun onActivityResumed(a: Activity) {}
                override fun onActivityPaused(a: Activity) { clear(a) }
                override fun onActivityStopped(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity, state: Bundle) {}
            })
        }
        keys[activity] = key
    }

    @Synchronized fun has(activity: Activity): Boolean = keys.containsKey(activity)
    @Synchronized fun take(activity: Activity): String = keys.remove(activity).orEmpty()
    @Synchronized fun clear(activity: Activity) { keys.remove(activity) }
}
