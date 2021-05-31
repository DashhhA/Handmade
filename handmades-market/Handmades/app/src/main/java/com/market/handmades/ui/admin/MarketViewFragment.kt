package com.market.handmades.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.market.handmades.AdminViewModel
import com.market.handmades.R
import com.market.handmades.model.MarketRaw
import com.market.handmades.remote.watchers.IWatcher
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.ui.Utils
import com.market.handmades.utils.AsyncResult
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MarketViewFragment: Fragment() {
    private val viewModel: AdminViewModel by activityViewModels()
    private var watcher: IWatcher<*>? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_view_market, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navigation = Navigation.findNavController(view)

        val pager: ViewPager2 = view.findViewById(R.id.pager)
        val tabLayout: TabLayout = view.findViewById(R.id.tab_layout)
        val mediator = TabLayoutMediator(tabLayout, pager) { tab, position ->
            tab.text = when(position) {
                0 -> getString(MarketInfoFragment.titleId)
                1 -> getString(MarketProductsFragment.titleId)
                else -> getString(MarketInfoFragment.titleId)
            }
        }

        val btnApprove: Button = view.findViewById(R.id.btn_approve)
        val btnBlock: Button = view.findViewById(R.id.btn_block)

        val progress = TintedProgressBar(requireContext(), view as ViewGroup)
        btnApprove.setOnClickListener { GlobalScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { progress.show() }

            val marketRepository = ConnectionActivity.getMarketRepository()

            val res = marketRepository.changeField(
                viewModel.selectedMarket?.dbId!!,
                MarketRaw.ChangableFields.status(MarketRaw.MarketStatus.Approved),
            )

            withContext(Dispatchers.Main) { progress.hide() }

            res.getOrShowError(requireContext())
        } }
        btnBlock.setOnClickListener { GlobalScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { progress.show() }

            val marketRepository = ConnectionActivity.getMarketRepository()

            val res = marketRepository.changeField(
                viewModel.selectedMarket?.dbId!!,
                MarketRaw.ChangableFields.status(MarketRaw.MarketStatus.Blocked),
            )

            withContext(Dispatchers.Main) { progress.hide() }

            res.getOrShowError(requireContext())
        } }

        GlobalScope.launch(Dispatchers.IO) {
            val marketId = viewModel.selectedMarket?.dbId ?: return@launch
            val marketRepository = ConnectionActivity.getMarketRepository()
            val marketWatcher = marketRepository.watchMarketRaw(marketId)
            watcher = marketWatcher

            withContext(Dispatchers.Main) {
                if (pager.adapter == null) {
                    val pagerAdapter = MarketPagerAdapter(
                        this@MarketViewFragment,
                        navigation,
                        marketWatcher.getData()
                    )
                    pager.adapter = pagerAdapter
                    mediator.attach()
                }

                marketWatcher.getData().observe(viewLifecycleOwner) { res ->
                    val market = res.getOrShowError(requireContext()) ?: return@observe

                    when(market.status) {
                        is MarketRaw.MarketStatus.Validating -> {
                            btnApprove.visibility = Button.VISIBLE
                            btnBlock.visibility = Button.VISIBLE
                        }
                        is MarketRaw.MarketStatus.Approved -> {
                            btnApprove.visibility = Button.GONE
                            btnBlock.visibility = Button.VISIBLE
                        }
                        is MarketRaw.MarketStatus.Blocked -> {
                            btnApprove.visibility = Button.VISIBLE
                            btnBlock.visibility = Button.GONE
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        GlobalScope.launch(Dispatchers.IO) {
            watcher?.close()
        }
        super.onDestroyView()
    }
}

private class MarketPagerAdapter(
    fragment: Fragment,
    private val navigation: NavController,
    private val marketData: LiveData<AsyncResult<MarketRaw>>
): FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int {
        return 2
    }

    override fun createFragment(position: Int): Fragment {
        return when(position) {
            0 -> MarketInfoFragment.getInstance(marketData)
            1 -> MarketProductsFragment.getInstance(navigation)
            else -> MarketInfoFragment.getInstance(marketData)
        }
    }

}