package com.market.handmades.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.market.handmades.CustomerViewModel
import com.market.handmades.R
import com.market.handmades.model.MarketRaw
import com.market.handmades.remote.watchers.IWatcher
import com.market.handmades.ui.ListFragment
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.utils.ConnectionActivity
import com.market.handmades.utils.FilterMarketsCity
import com.market.handmades.utils.FiltersMarketIncludesWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VendorMarketsFragment: ListFragment<MarketRaw>() {
    private val viewModel: CustomerViewModel by activityViewModels()
    private var watcher: IWatcher<*>? = null
    companion object {
        const val titleId = R.string.vendor_markets
        fun getInstance(): VendorMarketsFragment {
            return VendorMarketsFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_customer_view_vendor_markets, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navigation = Navigation.findNavController(requireActivity(), R.id.customer_nav_host_fragment)

        val adapter = MarketsListAdapter(requireContext())
        val list: ListView = view.findViewById(R.id.markets_list)
        list.adapter = adapter

        val progress = TintedProgressBar(requireContext(), view as ViewGroup)
        progress.show()
        GlobalScope.launch(Dispatchers.IO) {
            val user = viewModel.selectedVendor ?: return@launch
            val vendorRepository = ConnectionActivity.getVendorRepository()
            val watcher = vendorRepository.watchMarkets(user.modelId)
            this@VendorMarketsFragment.watcher = watcher

            withContext(Dispatchers.Main) {
                watcher.getData().observe(viewLifecycleOwner) { res ->
                    val markets = res.getOrShowError(requireContext()) ?: return@observe

                    list.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                        viewModel.selectedMarket = adapter.getItem(position) ?: return@OnItemClickListener
                        navigation.navigate(R.id.action_vendorViewFragment_to_marketViewFragment)
                    }

                    progress.hide()
                    progress.remove()
                    adapter.update(markets)
                }
            }
        }

        val filters = listOf(
            FilterMarketsCity(),
            FiltersMarketIncludesWord()
        )

        val sorts: Map<String, (MarketRaw, MarketRaw) -> Int> = mapOf(
            getString(R.string.sort_by_name) to { o1, o2 -> o1.name.compareTo(o2.name) },
        )

        setUpListControl(adapter, filters, sorts)
        setDefaultFilter { it.status is MarketRaw.MarketStatus.Approved }
    }

    override fun onDestroy() {
        GlobalScope.launch(Dispatchers.IO) {
            watcher?.close()
        }
        super.onDestroy()
    }
}