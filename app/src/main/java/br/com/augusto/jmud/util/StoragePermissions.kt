package br.com.augusto.jmud.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object StoragePermissions {

    fun required(): Array<String> {
        val permissions = mutableListOf<String>()
        permissions.add(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        )
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return permissions.toTypedArray()
    }

    fun allGranted(context: Context): Boolean =
        required().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
}
