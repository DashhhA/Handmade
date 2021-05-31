package com.market.handmades.ui

import android.content.Context
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.ui.NavigationUI
import com.market.handmades.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class ListFragment<T>: Fragment() {
    private lateinit var arrayAdapter: ControllableAdapterA<T, *, *>
    private lateinit var filters: List<FilterItem<T>>
    private lateinit var sorts: Map<String, (T, T) -> Int>
    private lateinit var sortsView: View
    private lateinit var sortsMenu: PopupMenu
    private var reInitSortsMenu = false
    private val selectedFilters = mutableSetOf<FilterItem<T>>()
    private var defaultFilter: (T) -> Boolean = { true }

    protected fun setUpListControl(
        arrayAdapter: ControllableAdapterA<T, *, *>,
        filters: List<FilterItem<T>>,
        sorts: Map<String, (T, T) -> Int>,
    ) {
        reInitSortsMenu = true
        setHasOptionsMenu(true)
        this.arrayAdapter = arrayAdapter
        this.sorts = sorts
        this.filters = filters
        arrayAdapter.setSort(sorts.values.first())
        arrayAdapter.setFilter { defaultFilter(it) }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.list_controll_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (reInitSortsMenu) {
            reInitSortsMenu = false
            sortsView = requireActivity().findViewById(R.id.filter)
            val menu = PopupMenu(requireContext(), sortsView)
            sorts.keys.forEach { menu.menu.add(it) }
            menu.menu.setGroupCheckable(Menu.NONE, true, true)
            menu.menu.getItem(0).isChecked = true
            menu.setOnMenuItemClickListener { sort ->
                sort.isChecked = true
                arrayAdapter.setSort(sorts[sort.title]!!)
                return@setOnMenuItemClickListener true
            }
            sortsMenu = menu
        }
        when(item.itemId) {
            R.id.filter -> {
                showFilters()
                return true
            }
            R.id.sort -> {
                showSorts()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    protected fun setDefaultFilter(filter: (T) -> Boolean) {
        defaultFilter = filter
        if (this::arrayAdapter.isInitialized) {
            arrayAdapter.setFilter { item ->
                defaultFilter(item) && filters.all { it.apply(item) }
            }
        }
    }

    private fun showFilters() {
        val list = ListView(requireContext())
        val adapter = FiltersArrayAdapter<T>(requireContext())
        list.adapter = adapter
        adapter.addAll(selectedFilters)
        adapter.addView.setOnClickListener {

            var selected = 0
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_filter)
                .setNegativeButton(R.string.str_cancel) { _, _ -> }
                .setPositiveButton(R.string.button_positive) { _, _ ->
                    GlobalScope.launch {
                        val filter = filters[selected]
                        val res = filter.add(requireContext())
                        if (res) withContext(Dispatchers.Main) { adapter.add(filter) }
                    }
                }.setSingleChoiceItems(
                    filters.map { it.selectStr(requireContext()) }.toTypedArray(),
                    selected
                ) { _, which -> selected = which }
                .show()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.selected_filters)
            .setPositiveButton(R.string.button_positive) { _, _ -> newFilters(adapter.getAll()) }
            .setView(list)
            .show()
    }

    private fun showSorts() {
        sortsMenu.show()
    }

    private fun newFilters(filters: List<FilterItem<T>>) {
        selectedFilters.clear()
        selectedFilters.addAll(filters)
        if (filters.isEmpty()) {
            arrayAdapter.setFilter { item -> defaultFilter(item) }
        } else {
            arrayAdapter.setFilter { item ->
                defaultFilter(item) && filters.all { it.apply(item) }
            }
        }
    }

    abstract class FilterItem<T> {
        private var action: ((T) -> Boolean)? = null
        abstract fun selectStr(context: Context): String
        abstract fun addedStr(context: Context): String
        abstract suspend fun setAction(context: Context): ((T) -> Boolean)?
        suspend fun add(context: Context): Boolean {
            action = setAction(context)
            return action != null
        }
        fun apply(item: T): Boolean {
            return if (action != null) action!!(item) else true
        }
    }

    private class FiltersArrayAdapter<T>(
        context: Context
    ): ArrayAdapter<FilterItem<T>>(context, R.layout.list_itm_filters_dialog) {
        private val mInflater: LayoutInflater = LayoutInflater.from(context)
        val addView: ImageButton = ImageButton(context)

        init {
            addView.setImageResource(R.drawable.baseline_add_24)
        }

        private data class ViewHolder(
            val name: TextView,
            val remove: ImageButton,
        )

        override fun getCount(): Int {
            return super.getCount() + 1
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            if (position == count - 1) {
                return addView
            } else {
                val rowView: View
                val holder: ViewHolder
                if (convertView == null || convertView.tag == null) {
                    rowView = mInflater.inflate(R.layout.list_itm_filters_dialog, parent, false)
                    holder = ViewHolder(
                        rowView.findViewById(R.id.name),
                        rowView.findViewById(R.id.remove)
                    )
                    rowView.tag = holder
                } else {
                    rowView = convertView
                    holder = rowView.tag as ViewHolder
                }

                val item = getItem(position) ?: return rowView

                holder.name.text = item.addedStr(context)
                holder.remove.setImageResource(R.drawable.baseline_remove_circle_outline_24)
                holder.remove.setOnClickListener {
                    remove(item)
                    notifyDataSetChanged()
                }

                return rowView
            }
        }

        fun getAll(): List<FilterItem<T>> {
            val all = mutableListOf<FilterItem<T>>()
            for (i in 0 until count - 1) {
                getItem(i)?.let { all.add(it) }
            }

            return all
        }

        override fun add(`object`: FilterItem<T>?) {
            for (i in 0 until count - 1) {
                if (getItem(i)?.equals(`object`) != false) return
            }
            super.add(`object`)
        }
    }
}