package com.market.handmades.ui

import android.view.View

object Utils {
    fun printPrice(price: Float): String {
        return "${price}p"
    }
    fun stripPrice(price: String): Float? {
        return price.dropLast(1).toFloatOrNull()
    }
    fun setOneVisible(view: View?, all: List<View>) {
        all.forEach { v ->
            if (v == view) {
                v.visibility = View.VISIBLE
            } else {
                v.visibility = View.GONE
            }
        }
    }
}