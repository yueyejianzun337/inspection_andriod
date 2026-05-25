package com.eqm.inspection.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.eqm.inspection.data.api.ApiClient

object ReportDownloader {

    fun download(context: Context, recordId: Int, serverUrl: String) {
        val url = "${serverUrl.trimEnd('/')}/vendor/download_report/$recordId"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("廠商巡檢報告 #$recordId")
            .setDescription("正在下載巡檢報告...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "VendorInspection_${recordId}.xlsx"
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        // 添加 JWT Token
        val token = ApiClient.currentToken
        if (token != null) {
            request.addRequestHeader("Authorization", "Bearer $token")
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }
}
