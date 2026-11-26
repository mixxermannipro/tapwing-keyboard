// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.ime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Translucent helper Activity used by TapwingInputMethodService to request runtime
 * permissions (microphone or camera) since Services cannot call requestPermissions().
 */
class PermissionBridgeActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permission = intent.getStringExtra(EXTRA_PERMISSION)
        if (permission.isNullOrBlank() || permission !in ALLOWED_PERMISSIONS) {
            finish()
            return
        }
        launcher.launch(permission)
    }

    companion object {
        private const val EXTRA_PERMISSION = "permission"
        internal val ALLOWED_PERMISSIONS: Set<String> = setOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        fun requestRecordAudio(context: Context) {
            launch(context, Manifest.permission.RECORD_AUDIO)
        }

        fun launch(context: Context, permission: String) {
            if (permission !in ALLOWED_PERMISSIONS) return
            val intent = Intent(context, PermissionBridgeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_PERMISSION, permission)
            }
            context.startActivity(intent)
        }
    }
}
