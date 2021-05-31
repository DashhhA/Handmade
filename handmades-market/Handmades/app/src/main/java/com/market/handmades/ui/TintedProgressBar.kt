package com.market.handmades.ui

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.market.handmades.R

class TintedProgressBar {
    private val layout: RelativeLayout
    private val root: ViewGroup?
    private var removed = false

    constructor(layout: RelativeLayout) {
        this.layout = layout
        layout.visibility = RelativeLayout.INVISIBLE
        root = null
    }

    constructor(context: Context, root: ViewGroup) {
        this.root = root
        layout = RelativeLayout(context)
        layout.layoutParams =
                RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        layout.setBackgroundResource(R.color.pb_tint)

        val progressbar = ProgressBar(context)
        val progressbarParams =
                RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        progressbarParams.addRule(RelativeLayout.CENTER_IN_PARENT)
        progressbar.layoutParams = progressbarParams
        layout.addView(progressbar)
        layout.visibility = RelativeLayout.INVISIBLE

        root.addView(layout)
    }

    fun show() {
        layout.visibility = RelativeLayout.VISIBLE
    }

    fun hide() {
        layout.visibility = RelativeLayout.INVISIBLE
    }

    fun remove() {
        if (!removed) {
            val ind = root?.indexOfChild(layout) ?: return
            root.removeViewAt(ind)
            removed = true
        }
    }
}