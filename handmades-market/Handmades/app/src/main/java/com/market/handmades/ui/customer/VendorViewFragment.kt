package com.market.handmades.ui.customer

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.ui.NavigationUI
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.market.handmades.CustomerViewModel
import com.market.handmades.R
import com.market.handmades.model.Chat
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.ui.messages.ChatActivity
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VendorViewFragment: Fragment() {
    private val viewModel: CustomerViewModel by activityViewModels()
    private lateinit var navigation: NavController
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_customer_view_vendor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigation = Navigation.findNavController(view)

        setHasOptionsMenu(true)

        val pagerAdapter = VendorPagerAdapter(this)
        val pager: ViewPager2 = view.findViewById(R.id.pager)
        pager.adapter = pagerAdapter
        val tabLayout: TabLayout = view.findViewById(R.id.tab_layout)
        TabLayoutMediator(tabLayout, pager) { tab, position ->
            tab.text = when(position) {
                0 -> getString(VendorInfoFragment.titleId)
                1 -> getString(VendorMarketsFragment.titleId)
                else -> getString(VendorInfoFragment.titleId)
            }
        }.attach()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.chat_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_message) {
            goToChat()
        }

        return NavigationUI.onNavDestinationSelected(item, navigation) ||
                super.onOptionsItemSelected(item)
    }

    private fun goToChat() {
        val chats = viewModel.chats.value
        val vendor = viewModel.selectedVendor!!
        if (chats != null) {
            val existing = chats.find { it.type is Chat.ChatType.Private && it.users.find { it.dbId == vendor.dbId } != null }
            if (existing != null) {
                val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                    putExtra(ChatActivity.EXTRA_CHAT_ID, existing.dbId)
                    val user = viewModel.user.value!!.asDTO()
                    putExtra(ChatActivity.EXTRA_USER_DTO, Gson().toJson(user))
                }
                startActivity(intent)
                return
            }
        }
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.ask_start_new_chat)
            .setNegativeButton(R.string.str_cancel) { _, _ -> }
            .setPositiveButton(R.string.str_yes) { _, _ ->
                val progress =TintedProgressBar(requireContext(), view as ViewGroup)
                progress.show()
                GlobalScope.launch(Dispatchers.IO) {
                    val chatRepository = ConnectionActivity.getChatRepository()
                    val res = chatRepository.newChat(Chat.ChatType.Private, listOf(vendor))
                    withContext(Dispatchers.Main) {
                        val ans = res.getOrShowError(requireContext()) ?: return@withContext
                        progress.hide(); progress.remove()
                        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                            putExtra(ChatActivity.EXTRA_CHAT_ID, ans.chatId)
                            val user = viewModel.user.value!!.asDTO()
                            putExtra(ChatActivity.EXTRA_USER_DTO, Gson().toJson(user))
                        }
                        startActivity(intent)
                    }
                }
            }.show()
    }
}

private class VendorPagerAdapter(fragment: Fragment): FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when(position) {
            0 -> VendorInfoFragment.getInstance()
            1 -> VendorMarketsFragment.getInstance()
            else -> VendorInfoFragment.getInstance()
        }
    }
}