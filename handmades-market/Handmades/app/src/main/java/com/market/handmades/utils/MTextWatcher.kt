package com.market.handmades.utils

import android.text.Editable
import android.text.TextWatcher

class MTextWatcher(
        private val onTextChanged: (String) -> Unit
): TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }

    override fun afterTextChanged(s: Editable?) {
        onTextChanged(s.toString())
    }
}