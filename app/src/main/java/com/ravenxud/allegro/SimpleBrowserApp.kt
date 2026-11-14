package com.ravenxud.allegro

import android.app.Application
import android.os.Build
import android.util.Log
import com.ravenxud.allegro.di.ServiceLocator
import com.ravenxud.allegro.util.DiagLog

class SimpleBrowserApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
        installCrashLogger()
        ServiceLocator.init(this)
    }

    /**
     * 把未捕获异常写进诊断日志，再交回系统默认处理。
     *
     * 为什么必须有：崩溃时进程直接重启，用户只能看到"闪退"，而 `DiagLog` 里
     * 只有业务事件、没有堆栈 —— 这次冒烟页闪退就吃了这个亏。
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                DiagLog.log(
                    "crash",
                    "thread=${thread.name} device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT}\n" +
                        Log.getStackTraceString(error),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
