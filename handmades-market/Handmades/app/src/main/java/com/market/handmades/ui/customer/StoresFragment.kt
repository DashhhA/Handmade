package com.market.handmades.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.market.handmades.CustomerViewModel
import com.market.handmades.R
import com.market.handmades.model.MarketRaw
import com.market.handmades.ui.ControllableAdapter
import com.market.handmades.ui.CustomViewArrayAdapter
import com.market.handmades.ui.WithStoresFragment
import com.market.handmades.utils.FilterMarketsCity
import com.market.handmades.utils.FiltersMarketIncludesWord

class StoresFragment: WithStoresFragment(R.id.markets_list) {
    private val viewModel: CustomerViewModel by activityViewModels()
    private val adapter by lazy { MarketsListAdapter(requireContext()) }
    init {
        setDefaultFilter { market -> market.status is MarketRaw.MarketStatus.Approved }
    }
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_customer_stores, container, false)
    }

    override fun getAdapter(): ControllableAdapter<MarketRaw, *> = adapter


    override fun getFilters(): List<FilterItem<MarketRaw>> = listOf(
        FilterMarketsCity(),
        FiltersMarketIncludesWord(),
    )

    override fun getSorts(): Map<String, (MarketRaw, MarketRaw) -> Int> = mapOf(
        getString(R.string.sort_by_name) to { o1, o2 -> o1.name.compareTo(o2.name) },
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState, viewModel.markets)
        val navigation = Navigation.findNavController(view)

        marketsList.setOnItemClickListener { _, _, position, _ ->
            viewModel.selectedMarketId = adapter.getItem(position)?.dbId ?: return@setOnItemClickListener
            navigation.navigate(R.id.action_customer_stores_to_marketViewFragment)
        }
    }
}