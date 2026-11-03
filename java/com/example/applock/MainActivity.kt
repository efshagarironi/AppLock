package com.example.applock

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("app_lock_settings", MODE_PRIVATE)
    }

    private val lockedApps = mutableSetOf<String>()

    private lateinit var appList: LinearLayout
    private lateinit var executor: Executor

    override fun onCreate(savedInstanceState: Bundle?) {

        val darkMode = getSharedPreferences(
            "app_lock_settings", MODE_PRIVATE
        ).getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (darkMode)
                AppCompatDelegate.MODE_NIGHT_YES
            else
                AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)

        window.decorView.layoutDirection =
            android.view.View.LAYOUT_DIRECTION_RTL

        lockedApps.addAll(
            prefs.getStringSet("locked_apps", emptySet()) ?: emptySet()
        )

        executor = ContextCompat.getMainExecutor(this)

        createInterface()
        loadApplications()
    }

    private fun createInterface() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 12)
        }

        val title = TextView(this).apply {
            text = "قفل هوشمند برنامه‌ها"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(8, 16, 8, 20)
        }

        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "برنامه‌هایی را که می‌خواهید قفل شوند انتخاب کنید"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 16)
        }

        root.addView(subtitle)

        val themeButton = Button(this).apply {
            text = "تغییر حالت روشن / تیره"
            setOnClickListener {
                val current = prefs.getBoolean("dark_mode", false)
                prefs.edit().putBoolean("dark_mode", !current).apply()
                recreate()
            }
        }

        root.addView(themeButton)

        val biometricButton = Button(this).apply {
            text = "آزمایش احراز هویت بیومتریک"
            setOnClickListener {
                authenticate()
            }
        }

        root.addView(biometricButton)

        val count = TextView(this).apply {
            text = "برنامه‌های انتخاب‌شده: ${lockedApps.size}"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(8, 18, 8, 12)
            gravity = Gravity.CENTER
            tag = "count"
        }

        root.addView(count)

        val scroll = ScrollView(this)

        appList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(appList)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    private fun loadApplications() {

        appList.removeAllViews()

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val applications = packageManager
            .queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != packageName }
            .sortedBy {
                it.loadLabel(packageManager).toString()
            }

        for (app in applications) {

            val appName = app.loadLabel(packageManager).toString()

            val checkBox = CheckBox(this).apply {

                text = appName
                textSize = 16f
                isChecked = lockedApps.contains(app.packageName)

                setPadding(12, 12, 12, 12)

                setOnCheckedChangeListener { _, checked ->

                    if (checked) {
                        lockedApps.add(app.packageName)
                    } else {
                        lockedApps.remove(app.packageName)
                    }

                    saveLockedApps()
                    updateCount()
                }
            }

            appList.addView(
                checkBox,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        updateCount()
    }

    private fun saveLockedApps() {

        prefs.edit()
            .putStringSet("locked_apps", lockedApps.toSet())
            .apply()
    }

    private fun updateCount() {

        val count = findViewByTag("count") as? TextView
        count?.text = "برنامه‌های انتخاب‌شده: ${lockedApps.size}"
    }

    private fun authenticate() {

        val biometricManager = BiometricManager.from(this)

        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        when (biometricManager.canAuthenticate(authenticators)) {

            BiometricManager.BIOMETRIC_SUCCESS -> {

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("احراز هویت")
                    .setSubtitle("با روش امنیتی گوشی هویت خود را تأیید کنید")
                    .setAllowedAuthenticators(authenticators)
                    .build()

                val prompt = BiometricPrompt(
                    this,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {

                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult
                        ) {
                            super.onAuthenticationSucceeded(result)

                            Toast.makeText(
                                this@MainActivity,
                                "احراز هویت موفق بود",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence
                        ) {
                            super.onAuthenticationError(
                                errorCode,
                                errString
                            )

                            Toast.makeText(
                                this@MainActivity,
                                errString,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )

                prompt.authenticate(promptInfo)
            }

            else -> {
                AlertDialog.Builder(this)
                    .setTitle("احراز هویت در دسترس نیست")
                    .setMessage(
                        "ابتدا قفل صفحه یا روش بیومتریک گوشی را فعال کنید."
                    )
                    .setPositiveButton("باشه", null)
                    .show()
            }
        }
    }
}
