package com.market.handmades.ui.admin

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import com.market.handmades.R
import com.market.handmades.model.Message
import com.market.handmades.utils.ConnectionActivity
import com.market.handmades.utils.MTextWatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object Utils {
    fun deleteMessage(context: Context, message: Message) {
        val input = EditText(context)
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.admin_msg_delete_reason)
            .setView(input)
            .setPositiveButton(R.string.button_positive) { _, _ ->
                val reason = input.text.toString()
                if (reason.isBlank()) return@setPositiveButton

                GlobalScope.launch(Dispatchers.IO) {
                    val chatRepository = ConnectionActivity.getChatRepository()
                    val res = chatRepository.deleteMessage(message.dbId, reason)

                    res.getOrShowError(context)
                }
            }
            .setNegativeButton(R.string.str_cancel) { _, _, -> }
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        input.error = context.getString(R.string.str_should_not_empty)

        input.addTextChangedListener(MTextWatcher { text ->
            if (text.isBlank()) {
                input.error = context.getString(R.string.str_should_not_empty)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            } else {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
            }
        })
    }
}