package com.market.handmades.ui.vendor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import androidx.navigation.ui.NavigationUI
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.market.handmades.R
import com.market.handmades.VendorActivity
import com.market.handmades.VendorViewModel
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StoresFragment : Fragment() {
    val vendorViewModel: VendorViewModel by activityViewModels()
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_vendor_stores, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navigation = Navigation.findNavController(view)

        val btnAddMarket: FloatingActionButton = view.findViewById(R.id.btn_add_market)
        btnAddMarket.setOnClickListener {
            navigation.navigate(R.id.action_vendor_stores_to_addMarketFragment)
        }

        val marketsListAdapter = MarketsListAdapter(requireContext())
        val marketsList: ListView = view.findViewById(R.id.markets_list)
        marketsList.adapter = marketsListAdapter
        val progress = TintedProgressBar(requireContext(), view as ViewGroup)
        progress.show()
        GlobalScope.launch(Dispatchers.IO) {
            val marketsData = VendorActivity.getSynchronizedData().markets
            withContext(Dispatchers.Main) {
                marketsList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                    vendorViewModel.selectedMarket = marketsListAdapter.getItem(position) ?: return@OnItemClickListener
                    navigation.navigate(R.id.action_vendor_stores_to_marketFragment)
                }
                marketsData.observe(viewLifecycleOwner) { markets ->
                    progress.hide()
                    progress.remove()
                    marketsListAdapter.update(markets)
                }
            }
        }
    }
}