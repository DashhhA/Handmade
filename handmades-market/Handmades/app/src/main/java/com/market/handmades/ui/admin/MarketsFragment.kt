package com.market.handmades.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.market.handmades.AdminViewModel
import com.market.handmades.R
import com.market.handmades.model.MarketRaw
import com.market.handmades.ui.ControllableAdapter
import com.market.handmades.ui.CustomViewArrayAdapter
import com.market.handmades.ui.ListFragment
import com.market.handmades.ui.WithStoresFragment
import com.market.handmades.ui.vendor.MarketsArrayAdapter
import com.market.handmades.utils.*

class MarketsFragment: WithStoresFragment(R.id.markets_list) {
    private val adapter by lazy { MarketsArrayAdapter(requireContext()) }
    private val viewModel: AdminViewModel by activityViewModels()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_markets, container, false)
    }

    override fun getAdapter(): ControllableAdapter<MarketRaw, *> {
        return adapter
    }


    override fun getFilters(): List<FilterItem<MarketRaw>> = listOf(
        FilterMarketsApproved(),
        FilterMarketsBlocked(),
        FilterMarketsValidating(),
        FilterMarketsCity(),
        FiltersMarketIncludesWord()
    )

    override fun getSorts(): Map<String, (MarketRaw, MarketRaw) -> Int> {
        return mapOf(
            getString(R.string.sort_by_name) to { o1, o2 -> o1.name.compareTo(o2.name) },
            "reverse" to { o1, o2 -> -o1.name.compareTo(o2.name) }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState, viewModel.markets)
        val navigation = Navigation.findNavController(view)

        marketsList.setOnItemClickListener { _, _, position, _ ->
            viewModel.selectedMarket = adapter.getItem(position) ?: return@setOnItemClickListener
            navigation.navigate(R.id.action_admin_markets_to_marketViewFragment2)
        }
    }
}