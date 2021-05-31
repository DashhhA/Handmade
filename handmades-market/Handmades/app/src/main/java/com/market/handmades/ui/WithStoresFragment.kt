package com.market.handmades.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import com.market.handmades.model.Market
import com.market.handmades.model.MarketRaw
import com.market.handmades.remote.FileStream
import com.market.handmades.ui.customer.MarketsListAdapter
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class WithStoresFragment(
    private val listId: Int
): ListFragment<MarketRaw>() {
    private val photos: MutableList<FileStream.FileDescription> = mutableListOf()
    protected lateinit var marketsList: ListView

    protected abstract fun getAdapter(): ControllableAdapter<MarketRaw, *>
    protected abstract fun getFilters(): List<FilterItem<MarketRaw>>
    protected abstract fun getSorts(): Map<String, (MarketRaw, MarketRaw) -> Int>

    fun onViewCreated(view: View, savedInstanceState: Bundle?, marketsData: LiveData<List<Market>>) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = getAdapter()
        marketsList = view.findViewById(listId)
        marketsList.adapter = adapter

        val progress = TintedProgressBar(requireContext(), view as ViewGroup)
        progress.show()
        marketsData.observe(viewLifecycleOwner) { markets ->
            progress.hide(); progress.remove()

            GlobalScope.launch(Dispatchers.IO) {
                val wPhotos = markets.map { newMarketWP(it) }

                withContext(Dispatchers.Main) { adapter.update(wPhotos) }
            }
        }
        setUpListControl(adapter, getFilters(), getSorts())
    }

    private suspend fun newMarketWP(market: Market): MarketRaw {
        if (market.imageUrl == null)
            return MarketRaw(market.dto, null, market.dbId)

        val found = photos.find {  it.name == market.imageUrl }
        if (found != null) return MarketRaw(market.dto, found, market.dbId)

        val connection = ConnectionActivity.awaitConnection()
        val res = connection.fileStream.getFile(market.imageUrl)
        val newPhoto = res.getOrShowError(requireContext())

        if (newPhoto != null) {
            photos.add(newPhoto)
            return MarketRaw(market.dto, newPhoto, market.dbId)
        }

        return MarketRaw(market.dto, null, market.dbId)
    }
}