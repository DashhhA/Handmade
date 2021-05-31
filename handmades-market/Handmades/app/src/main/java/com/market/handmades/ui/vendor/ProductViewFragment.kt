package com.market.handmades.ui.vendor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.market.handmades.R
import com.market.handmades.VendorViewModel
import com.market.handmades.model.User
import com.market.handmades.ui.messages.MessagingFragment

class ProductViewFragment: Fragment() {
    private val viewModel: VendorViewModel by activityViewModels()
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_vendor_view_product, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navigation = Navigation.findNavController(view)

        val pagerAdapter = ProductPagerAdapter(
            this,
            navigation,
            viewModel.user.value!!,
            viewModel.selectedProduct!!.chatId
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