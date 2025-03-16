package com.android_package_installer

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import java.io.FileInputStream
import java.io.IOException
import android.util.Log
import java.io.File

internal class Installer(private val context: Context, private var activity: Activity?) {
    private var sessionId: Int = 0
    private lateinit var session: PackageInstaller.Session
    private lateinit var packageManager: PackageManager
    private lateinit var packageInstaller: PackageInstaller

    fun setActivity(activity: Activity?) {
        this.activity = activity
    }

    fun installPackage(apkPaths: Array<String>) {
        try {
            session = createSession()
            for (apkPath in apkPaths) {
                loadAPKFile(apkPath, session)
            }
            try {
                val intent = Intent(context, (activity ?: context).javaClass)
                intent.action = packageInstalledAction
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_MUTABLE
                )
                val statusReceiver = pendingIntent.intentSender
                session.commit(statusReceiver)
                session.close()
                Log.d("S1", "Session committed successfully.")
            } catch (e: Exception) {
                Log.e("E3", "Error committing session: ${e.message}")
                throw e
            }
        } catch (e: IOException) {
            throw RuntimeException("IO exception", e)
        } catch (e: Exception) {
            Log.e("E1", e.toString())
            session.abandon()
            throw e
        }
    }

    private fun createSession(): PackageInstaller.Session {
        try {
            packageManager = context.packageManager
            packageInstaller = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                params.setInstallReason(PackageManager.INSTALL_REASON_USER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                params.setRequestUpdateOwnership(true)
            }
            sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)
        } catch (e: Exception) {
            Log.e("E2", e.toString())
            throw e
        }
        return session
    }

    @Throws(IOException::class)
    private fun loadAPKFile(apkPath: String, session: PackageInstaller.Session) {
        val fileName = File(apkPath).name
        session.openWrite(fileName, 0, -1).use { output ->
            FileInputStream(apkPath).use { input ->
                val buffer = ByteArray(16384)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        }
    }
}

