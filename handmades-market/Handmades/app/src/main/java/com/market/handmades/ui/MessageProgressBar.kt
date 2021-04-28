package com.market.handmades.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.market.handmades.R

class MessageProgressBar(context: Context, message: String) {
    private val dialog: AlertDialog
    init {
        val layoutInflater = LayoutInflater.from(context)
        val  progressView = layoutInflater.inflate(R.layout.text_progress_bar, null)
        val textView: TextView = progressView.findViewById(R.id.message)
        textView.text = message
        val builder = AlertDialog.Builder(context).setView(progressView)
        dialog = builder.create()
    }

    fun show() {
        dialog.show()
    }

    fun hide() {
        dialog.hide()
    }
}