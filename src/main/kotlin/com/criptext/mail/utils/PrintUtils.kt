package com.criptext.mail.utils

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import com.criptext.mail.R


object PrintUtils{
    fun createWebPrintJob(webView: WebView, context: Context, documentName: String) {

        val printManager = context
                .getSystemService(Context.PRINT_SERVICE) as PrintManager

        val printAdapter = webView.createPrintDocumentAdapter(documentName.replace(Regex("[^\u0000-\u00FF]"), "").trim())

        val jobName = context.getString(R.string.app_name) + " Print Test"

        printManager.print(jobName, printAdapter,
                PrintAttributes.Builder().build())
    }
}