package com.sharesafe.app.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

/**
 * Opt-in screenshot watcher: on every app open we ask MediaStore for the newest
 * file under Pictures/Screenshots newer than the last dismissed one. The user
 * gets a "new screenshot — redact it?" card on Home. Requires the media-images
 * permission which is requested lazily, only when the toggle is enabled.
 */
object ScreenshotWatcher {

    fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, requiredPermission()) ==
            PackageManager.PERMISSION_GRANTED

    /** Newest screenshot URI strictly newer than `sinceSec` (MediaStore epoch seconds). */
    fun latestSince(context: Context, sinceSec: Long): Pair<Uri, Long>? {
        if (!hasPermission(context)) return null
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val selection = "(" +
            MediaStore.Images.Media.RELATIVE_PATH + " LIKE ? OR " +
            MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?)" +
            " AND " + MediaStore.Images.Media.DATE_ADDED + " > ?"
        val args = arrayOf("%Screenshots%", "Screenshot%", sinceSec.toString())
        val sort = MediaStore.Images.Media.DATE_ADDED + " DESC"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, selection, args, sort,
        )?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                val added = c.getLong(1)
                return Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString(),
                ) to added
            }
        }
        return null
    }
}
