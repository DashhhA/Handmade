package com.market.handmades.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.market.handmades.CustomerViewModel
import com.market.handmades.R
import com.market.handmades.model.User
import com.market.handmades.ui.messages.MessagingFragment
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductViewFragment: Fragment() {
    val viewModel: CustomerViewModel by activityViewModels()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_customer_view_product, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navigation = Navigation.findNavController(view)

        val pagerAdapter = ProductPagerAdapter(
            this,
            navigation,
            viewModel.user.value!!,
            viewModel.selectedProduct!!.product.chatId
        ) // TODO: check nulls
        val pager: ViewPager2 = view.findViewById(R.id.pager)
        pager.adapter = pagerAdapter
        val tabLayout: TabLayout = view.findViewById(R.id.tab_layout)
        TabLayoutMediator(tabLayout, pager) { tab, position ->
            tab.text = when(position) {
                0 -> getString(ProductAboutFragment.titleResId)
                1 -> getString(R.string.reviews)
                else -> getString(ProductAboutFragment.titleResId)
            }
        }.attach()

        val toVendor: Button = view.findViewById(R.id.to_vendor)
        val toMarket: Button = view.findViewById(R.id.to_market)

        toVendor.setOnClickListener { GlobalScope.launch(Dispatchers.IO) {
            val marketId = viewModel.selectedProduct?.product?.marketId ?: return@launch
            val marketRepository = ConnectionActivity.getMarketRepository()
            val vendorRepository = ConnectionActivity.getVendorRepository()
            val userRepository = ConnectionActivity.getUserRepository()
            val mRes = marketRepository.getMarketDTO(marketId)
            val market = mRes.getOrShowError(requireContext()) ?: return@launch
            val vRes = vendorRepository.getVendorDTO(market.vendorId)
            val vendor = vRes.getOrShowError(requireContext()) ?: return@launch
            val uRes = userRepository.getUser(vendor.userId)
            val user = uRes.getOrShowError(requireContext()) ?: return@launch

            viewModel.selectedVendor = user

            withContext(Dispatchers.Main) {
                navigation.navigate(R.id.action_aboutOrderFragment_to_vendorViewFragment)
            }
        } }

        toMarket.setOnClickListener {
            val marketId = viewModel.selectedProduct?.product?.marketId ?: return@setOnClickListener
            viewModel.selectedMarketId = marketId
            navigation.navigate(R.id.action_aboutProductFragmentC_to_marketViewFragment)
        }
    }
}

private class ProductPagerAdapter(
    fragment: Fragment,
    private val navigation: NavController,
    private val user: User,
    private val chatId: String
): FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when(position) {
            0 -> ProductAboutFragment.getInstance(navigation)
            1 -> MessagingFragment(user, chatId)
            else -> ProductAboutFragment.getInstance(navigation)
        }
    }
}