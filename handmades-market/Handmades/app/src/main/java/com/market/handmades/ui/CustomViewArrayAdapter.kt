package com.market.handmades.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter

abstract class CustomViewArrayAdapter<T, ViewHolderType>
private constructor(
        context: Context,
        private val objects: MutableList<T>,
        private val viewResource: Int,
): ArrayAdapter<T>(context, viewResource, objects) {
    constructor(context: Context, viewResource: Int): this(context, mutableListOf(), viewResource)
    private val mInflater: LayoutInflater
    init {
        mInflater = LayoutInflater.from(context)
    }

    abstract fun onGetView(holder: ViewHolderType, item: T, position: Int)
    abstract fun getViewHolderInstance(view: View, position: Int): ViewHolderType

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val rowView: View
        val holder: ViewHolderType
        if (convertView == null) {
            rowView = mInflater.inflate(viewResource, parent, false)
            holder = getViewHolderInstance(rowView, position)
            rowView.setTag(holder)
        } else {
            rowView = convertView
            holder = rowView.tag as ViewHolderType
        }

        val item = getItem(position) ?: return rowView

        onGetView(holder, item, position)

        return rowView
    }

    fun removeByFilter(filter: (T) -> Boolean) {
        objects.removeAll(filter)
        notifyDataSetChanged()
    }

    open fun update(items: Collection<T>) {
        clear()
        addAll(items)
    }
}