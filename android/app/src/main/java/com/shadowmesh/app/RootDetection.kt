
package com.shadowmesh.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

object RootDetection {
    fun isRooted(context: Context): Boolean {
        val checks = listOf(
            checkRootBinaries(),
            checkRootPaths(),
            checkBuildTags(),
            checkDangerousPackages(context),
            checkForRwPaths()
        )
        
        return checks.any { it }
    }
    
    private fun checkRootBinaries(): Boolean {
        val binaries = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        
        for (binary in binaries) {
            if (File(binary).exists()) {
                return true
            }
        }
        
        return false
    }
    
    private fun checkRootPaths(): Boolean {
        val paths = listOf(
            "/system/xbin/which",
            "/system/bin/which",
            "/system/xbin/ls",
            "/system/bin/ls"
        )
        
        for (path in paths) {
            if (File(path).exists()) {
                val process = Runtime.getRuntime().exec(arrayOf(path, "su"))
                val input = process.inputStream.bufferedReader()
                if (input.readLine() != null) {
                    return true
                }
            }
        }
        
        return false
    }
    
    private fun checkBuildTags(): Boolean {
        return Build.TAGS?.contains("test-keys") == true
    }
    
    private fun checkDangerousPackages(context: Context): Boolean {
        val packages = listOf(
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.zachspong.temprootremovejb",
            "com.amphoras.hidemyroot",
            "com.amphoras.hidemyrootadfree",
            "com.formyhm.hiderootPremium",
            "com.formyhm.hideroot",
            "com.devadvance.rootcloak",
            "com.devadvance.rootcloakplus",
            "de.robv.android.xposed.installer",
            "com.saurik.substrate",
            "com.chelpus.luckypatcher",
            "com.chelpus.lackypatch",
            "com.dimonvideo.luckypatcher"
        )
        
        val pm = context.packageManager
        for (pkg in packages) {
            try {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // Package not found, continue
            }
        }
        
        return false
    }
    
    private fun checkForRwPaths(): Boolean {
        val paths = listOf("/system", "/system/bin", "/system/xbin", "/sbin", "/vendor/bin")
        for (path in paths) {
            val file = File(path)
            if (file.canWrite()) {
                return true
            }
        }
        return false
    }
    
    fun getDetectionDetails(context: Context): String {
        val details = mutableListOf<String>()
        
        if (checkRootBinaries()) details.add("Root binaries found")
        if (checkRootPaths()) details.add("Root paths detected")
        if (checkBuildTags()) details.add("Test keys detected")
        if (checkDangerousPackages(context)) details.add("Dangerous packages found")
        if (checkForRwPaths()) details.add("Writable system partition")
        
        return if (details.isEmpty()) "Device not rooted" else details.joinToString(", ")
    }
}
