package com.pdd.glassbar.core

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.pdd.glassbar.loader.HookBridge
import java.lang.ref.WeakReference

/**
 * 锚点侦察模式: 对暂无 Profile 的包, 通用 hook Activity 生命周期,
 * 自动输出 Activity 类名与完整视图树(类名/id名/子项数/边界) 到日志。
 * 用于为新 App 快速发现 底栏宿主类/层级结构, 之后即可编写正式 Profile。
 */
object Recon {

    private val dumpedActivities = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val pending = java.util.concurrent.ConcurrentHashMap.newKeySet<
        WeakReference<Activity>>()

    fun install(b: HookBridge) {
        val actCls = try { b.hostClassLoader.loadClass("android.app.Activity") } catch (t: Throwable) {
            b.log(t); return
        }
        runCatching {
            val m = actCls.getDeclaredMethod("onCreate", Bundle::class.java)
            m.isAccessible = true
            b.hookAfter(m) { f ->
                val act = f.thisObject as? Activity ?: return@hookAfter
                runCatching { com.pdd.glassbar.ui.ModuleFileLogCompat.init(act) }
                pending += java.lang.ref.WeakReference(act)
                ModuleFileLogCompat.write("RECON activity-created " + act.javaClass.name)
                scheduleDump(act)
            }
            ReconLog.tagOk("recon armed")
        }.onFailure { t -> b.log("recon FAILED"); b.log(t) }
    }

    private fun scheduleDump(act: Activity) {
        val name = act.javaClass.name
        act.window?.decorView?.postDelayed({
            runCatching {
                if (!dumpedActivities.add(name)) return@runCatching
                val root = act.window?.decorView ?: return@runCatching
                ModuleFileLogCompat.write("RECON tree-begin $name")
                dump(root, 0, cap = 400)
                ModuleFileLogCompat.write("RECON tree-end $name")
            }
        }, 1200)
    }

    private fun dump(v: View, depth: Int, cap: Int): Int {
        if (depth > 14) return 0
        val pad = "  ".repeat(depth)
        val idName = try {
            val res = v.context.resources
            res.getResourceEntryName(v.id)
        } catch (_: Throwable) { "-" }
        var n = 1
        ModuleFileLogCompat.write(
            "$pad${v.javaClass.name} id=$idName vis=${v.visibility} " +
                "[${v.left},${v.top} ${v.width}x${v.height}] child=${if (v is ViewGroup) v.childCount else 0}"
        )
        if (n >= cap) return n
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                n += dump(v.getChildAt(i), depth + 1, cap - n)
                if (n >= cap) break
            }
        }
        return n
    }
}
