package com.market.handmades.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MediatorLiveData
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.market.handmades.CustomerViewModel
import com.market.handmades.R
import com.market.handmades.model.Order
import com.market.handmades.ui.messages.MessagingFragment

class OrderViewFragment: Fragment() {
    val viewModel: CustomerViewModel by activityViewModels()
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_customer_view_order, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pagerAdapter = OrderViewPagerAdapter(this)
        val pager: ViewPager2 = view.findViewById(R.id.pager)
        pager.adapter = pagerAdapter
        val tabLayout: TabLayout = view.findViewById(R.id.tab_layout)
        TabLayoutMediator(tabLayout, pager) { tab, position ->
            tab.text = when(position) {
                0 -> getString(OrderInfoFragment.titleId)
                1 -> getString(OrderContentFragment.titleId)
                else -> getString(OrderInfoFragment.titleId)
            }
        }.attach()
    }
}

private class OrderViewPagerAdapter(fragment: Fragment): FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when(position) {
            0 -> OrderInfoFragment.getInstance()
            1 -> OrderContentFragment.getInstance()
            else -> OrderInfoFragment.getInstance()
        }
    }
}