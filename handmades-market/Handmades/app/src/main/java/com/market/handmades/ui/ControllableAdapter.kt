package com.market.handmades.ui

import android.content.Context

abstract class ControllableAdapter<T, R>(
    context: Context,
    viewResource: Int,
): ControllableAdapterA<T, T, R>(context, viewResource) {
    override fun addAll(items: Collection<T>) {
        super.addAll(items)
    }
}

abstract class ControllableAdapterA<T, R, K>(
    context: Context,
    viewResource: Int,
): CustomViewArrayAdapter<R, K>(context, viewResource) {
    private var unfiltered: List<T> = listOf()
    private var filtered: List<T> = listOf()
    private var mFilter: (T) -> Boolean = { true }
    private var mSort: (o1: T, o2: T) -> Int = { _, _ -> 0 }
    fun setFilter(filter: (T) -> Boolean) {
        this.mFilter = filter
        update(unfiltered)
    }

    fun setSort(sort: (o1: T, o2: T) -> Int) {
        this.mSort = sort
        update(unfiltered)
    }

    fun update(items: Collection<T>) {
        unfiltered = items.toList()
        filtered = items.filter(mFilter)
        val sorted = filtered.sortedWith(mSort)
        clear()
        addAll(sorted)
    }

    internal abstract fun addAll(items: Collection<T>)
}