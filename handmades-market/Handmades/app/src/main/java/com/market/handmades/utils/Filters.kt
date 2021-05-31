package com.market.handmades.utils

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ListView
import com.market.handmades.R
import com.market.handmades.model.MarketRaw
import com.market.handmades.model.Message
import com.market.handmades.model.Order
import com.market.handmades.model.ProductWP
import com.market.handmades.ui.ListFragment
import com.market.handmades.ui.vendor.TagListAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class FilterMarketsApproved: ListFragment.FilterItem<MarketRaw>() {
    override fun selectStr(context: Context): String = context.getString(R.string.filter_approved)

    override fun addedStr(context: Context): String = context.getString(R.string.filter_approved)

    override suspend fun setAction(context: Context): ((MarketRaw) -> Boolean) {
        return { marketRaw -> marketRaw.status is MarketRaw.MarketStatus.Approved }
    }
}

class FilterMarketsBlocked: ListFragment.FilterItem<MarketRaw>() {
    override fun selectStr(context: Context): String = context.getString(R.string.filter_blocked)

    override fun addedStr(context: Context): String = context.getString(R.string.filter_blocked)

    override suspend fun setAction(context: Context): ((MarketRaw) -> Boolean) {
        return { marketRaw -> marketRaw.status is MarketRaw.MarketStatus.Blocked }
    }
}

class FilterMarketsValidating: ListFragment.FilterItem<MarketRaw>() {
    override fun selectStr(context: Context): String = context.getString(R.string.filter_validating)

    override fun addedStr(context: Context): String = context.getString(R.string.filter_validating)

    override suspend fun setAction(context: Context): ((MarketRaw) -> Boolean) {
        return { marketRaw -> marketRaw.status is MarketRaw.MarketStatus.Validating }
    }
}

class FilterMarketsCity: ListFragment.FilterItem<MarketRaw>() {
    private lateinit var city: String
    override fun selectStr(context: Context): String = context.getString(R.string.filter_by_city)

    override fun addedStr(context: Context): String = context.getString(R.string.filter_city, city)

    override suspend fun setAction(context: Context): ((MarketRaw) -> Boolean)? {
        return suspendCoroutine { continuation ->
            val input = EditText(context)
            input.setHint(R.string.v_add_market_city_hint)
            val dialog = AlertDialog.Builder(context)
                .setView(input)
                .setPositiveButton(R.string.button_positive) { _, _ ->
                    val city = input.text.toString()
                    if (city.isBlank()) {
                        continuation.resume(null)
                    } else {
                        this.city = city
                        continuation.resume { market -> market.city == city }
                    }
                }.setNegativeButton(R.string.str_cancel) { _, _ -> continuation.resume(null) }

            GlobalScope.launch(Dispatchers.Main) { dialog.show() }
        }
    }
}

class FiltersMarketIncludesWord: ListFragment.FilterItem<MarketRaw>() {
    private var word = ""
    override fun selectStr(context: Context): String = context.getString(R.string.filter_includes_word)

    override fun addedStr(context: Context): String =
        context.getString(R.string.filter_includes_word_added, word)

    override suspend fun setAction(context: Context): ((MarketRaw) -> Boolean)? {
        val text = EditText(context)

        return suspendCoroutine { continuation ->
            val dialog = AlertDialog.Builder(context)
                .setView(text)
                .setTitle(R.string.filter_includes_word_word_title)
                .setNegativeButton(R.string.str_cancel) { _, _ -> continuation.resume(null) }
                .setPositiveButton(R.string.button_positive) { _, _, ->
                    val word = text.text.toString()
                    if (word.isBlank()) {
                        continuation.resume(null)
                    } else {
                        this.word = word
                        continuation.resume { marketRaw -> marketRaw.name.contains(word) }
                    }
                }

            GlobalScope.launch(Dispatchers.Main) { dialog.show() }
        }
    }
}

class FilterProductsPriceInterval: ListFragment.FilterItem<ProductWP>() {
    private var low: Float = Float.MIN_VALUE
    private var up: Float = Float.MAX_VALUE

    override fun selectStr(context: Context): String = context.getString(R.string.filter_price_interval)

    override fun addedStr(context: Context): String = context.getString(
        R.string.filter_price_interval_added,
        if (low != Float.MIN_VALUE) low.toString() else context.getString(R.string.str_unlimited),
        if (up != Float.MAX_VALUE) up.toString() else context.getString(R.string.str_unlimited),
    )

    override suspend fun setAction(context: Context): ((ProductWP) -> Boolean)? {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.dialog_interval, null)
        val lLim: EditText = view.findViewById(R.id.lower_limit)
        val hLim: EditText = view.findViewById(R.id.upper_limit)

        return suspendCoroutine { continuation ->
            val dialog = AlertDialog.Builder(context)
                .setView(view)
                .setNegativeButton(R.string.str_cancel) { _, _ -> continuation.resume(null) }
                .setPositiveButton(R.string.button_positive) { _, _ ->
                    low = lLim.text.toString().toFloatOrNull() ?: Float.MIN_VALUE
                    up = hLim.text.toString().toFloatOrNull() ?: Float.MAX_VALUE
                    continuation.resume { productWP -> productWP.product.price in low..up }
                }

            GlobalScope.launch(Dispatchers.Main) { dialog.show() }
        }
    }
}

class FilterProductsIncludeTags: ListFragment.FilterItem<ProductWP>() {
    private var tags = listOf<String>()

    override fun selectStr(context: Context): String = context.getString(R.string.filter_include_tags)

    override fun addedStr(context: Context): String {
        val str = if (tags.isNotEmpty()) {
            tags.reduce { acc, s -> "$acc, $s" }
        } else context.getString(R.string.str_none)
        return context.getString(R.string.filter_include_tags_added, str)
    }

    override suspend fun setAction(context: Context): ((ProductWP) -> Boolean)? {
        val adapter = TagListAdapter(context)
        adapter.addView.setOnClickListener {
            val text = EditText(context)
            AlertDialog.Builder(context)
                .setTitle(R.string.v_add_market_add_tag_title)
                .setNegativeButton(R.string.str_cancel) { _, _, -> }
                .setPositiveButton(R.string.button_positive) { _, _ ->
                    if (text.text.isNotBlank()) adapter.add(text.text.toString())
                }
                .setView(text)
                .show()
        }
        val list = ListView(context)
        list.adapter = adapter

        return suspendCoroutine { continuation ->
            val dialog = AlertDialog.Builder(context)
                .setView(list)
                .setTitle(R.string.v_add_market_edit_tags_title)
                .setPositiveButton(R.string.button_positive) { _, _ ->
                    tags = adapter.getAll()
                    continuation.resume { productWP -> tags.contains(productWP.product.tag) }
                }

            GlobalScope.launch(Dispatchers.Main) { dialog.show() }
        }
    }
}

class FilterProductIncludesWord: ListFragment.FilterItem<ProductWP>() {
    private var word = ""
    override fun selectStr(context: Context): String = context.getString(R.string.filter_includes_word)

    override fun addedStr(context: Context): String =
        context.getString(R.string.filter_includes_word_added, word)

    override suspend fun setAction(context: Context): ((ProductWP) -> Boolean)? {
        val text = EditText(context)

        return suspendCoroutine { continuation ->
            val dialog = AlertDialog.Builder(context)
                .setView(text)
                .setTitle(R.string.filter_includes_word_word_title)
                .setNegativeButton(R.string.str_cancel) { _, _ -> continuation.resume(null) }
                .setPositiveButton(R.string.button_positive) { _, _, ->
                    val word = text.text.toString()
                    if (word.isBlank()) {
                        continuation.resume(null)
                    } else {
                        this.word = word
                        continuation.resume { productWP -> productWP.product.name.contains(word) }
                    }
                }

            GlobalScope.launch(Dispatchers.Main) { dialog.show() }
        }
    }
}

class FilterOrdersByStatus: ListFragment.FilterItem<Order>() {
    private var selected = ""
    override fun selectStr(context: Context): String = context.getString(R.string.filter_orders_by_status)

    override fun addedStr(context: Context): String
        = context.getString(R.string.filter_select_status_added, selected)

    override suspend fun setAction(context: Context): ((Order) -> Boolean)? {
        val items = arrayOf(
            context.getString(Order.OrderStatus.Posted.userStrId),
            context.getString(Order.OrderStatus.AwaitPay.userStrId),
            context.getString(Order.OrderStatus.Paid.userStrId),
            context.getString(Order.OrderStatus.Shipped.userStrId),
            context.getString(Order.OrderStatus.Delivered.userStrId),
        )
        var selected = 0
        return suspendCoroutine { continuation ->
            val dialog = AlertDialog.Builder(context)
                .setSingleChoiceItems(items, 0) { _, which -> selected = which }
                .setTitle(R.string.filter_select_status)
                .setNegativeButton(R.string.str_cancel) { _, _, -> continuation.resume(null) }
                .setPositiveButton(R.string.button_positive) { _, _ ->
                    val item = items[selected]
                    this.selected = item
                    continuation.resume { order -> context.getString(order.status.userStrId) == item }
                }

            GlobalScope.launch(Dispatchers.Main) { dialog.show() }
        }
    }
}

class FilterOrderUrgent: ListFragment.FilterItem<Order>() {
    override fun selectStr(context: Context): String = context.getString(R.string.filter_order_urgent)

    override fun addedStr(context: Context): String = context.getString(R.string.filter_order_urgent)

    override suspend fun setAction(context: Context): ((Order) -> Boolean) {
        return { order -> order.urgent.checked }
    }
}

class FilterOrderGift: ListFragment.FilterItem<Order>() {
    override fun selectStr(context: Context): String = context.getString(R.string.filter_order_gift)

    override fun addedStr(context: Context): String = context.getString(R.string.filter_order_gift)

    override suspend fun setAction(context: Context): ((Order) -> Boolean) {
        return { order -> order.packing.checked }
    }
}

class FilterMessagesDeleted: ListFragment.FilterItem<Message>() {
    override fun selectStr(context: Context): String = context.getString(R.string.admin_all_messages_deleted)

    override fun addedStr(context: Context): String = context.getString(R.string.admin_all_messages_deleted)

    override suspend fun setAction(context: Context): ((Message) -> Boolean) {
        return { it.deleted != null }
    }
}

class FilterMessagesUndeleted: ListFragment.FilterItem<Message>() {
    override fun selectStr(context: Context): String = context.getString(R.string.admin_all_messages_undeleted)

    override fun addedStr(context: Context): String = context.getString(R.string.admin_all_messages_undeleted)

    override suspend fun setAction(context: Context): ((Message) -> Boolean) {
        return { it.deleted == null }
    }
}
