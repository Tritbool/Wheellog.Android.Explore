package com.cooper.wheellog.views

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.cooper.wheellog.DialogHelper.setBlackIcon
import com.cooper.wheellog.R
import com.cooper.wheellog.data.TripItemState
import com.cooper.wheellog.utils.ThemeIconEnum
import com.cooper.wheellog.utils.ThemeManager

object TripActions {
    fun shareIntent(context: Context, trip: TripItemState): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, trip.uri)
            clipData = ClipData.newRawUri(trip.fileName, trip.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, null
    ).apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }

    fun confirmDelete(context: Context, trip: TripItemState, delete: (TripItemState) -> Unit): AlertDialog =
        AlertDialog.Builder(context)
            .setTitle(R.string.trip_menu_delete_file)
            .setMessage(context.getString(R.string.trip_menu_delete_file_confirmation) + " " + trip.fileName)
            .setCancelable(false)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(android.R.string.ok) { _, _ -> delete(trip) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show().also { it.setBlackIcon() }

    fun menu(anchor: View, trip: TripItemState, delete: (TripItemState) -> Unit, appTheme: Int = ThemeManager.theme) {
        val context = anchor.context
        PopupMenu(ContextThemeWrapper(context, R.style.OriginalTheme_PopupMenuStyle), anchor).apply {
            menu.add(0, 2, 2, R.string.trip_menu_share).setIcon(ThemeManager.getId(ThemeIconEnum.TripsShare, appTheme))
            menu.add(0, 3, 3, R.string.trip_menu_delete_file).setIcon(ThemeManager.getId(ThemeIconEnum.TripsDelete, appTheme))
            setOnMenuItemClickListener {
                when (it.itemId) {
                    2 -> {
                        try { context.startActivity(shareIntent(context, trip)) }
                        catch (_: Exception) { Toast.makeText(context, R.string.trip_share_failed, Toast.LENGTH_LONG).show() }
                        true
                    }
                    3 -> { confirmDelete(context, trip, delete); true }
                    else -> false
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setForceShowIcon(true)
            show()
        }
    }
}
