package com.market.handmades.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.market.handmades.CustomerViewModel
import com.market.handmades.R
import com.market.handmades.model.User
import com.market.handmades.remote.watchers.IWatcher
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VendorInfoFragment: Fragment() {
    private val viewModel: CustomerViewModel by activityViewModels()
    private var watcher: IWatcher<User>? = null
    companion object {
        const val titleId = R.string.vendor_about
        fun getInstance(): VendorInfoFragment {
            return VendorInfoFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_customer_vendor_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fName: TextView = view.findViewById(R.id.name)
        val sName: TextView = view.findViewById(R.id.sname)
        val surname: TextView = view.findViewById(R.id.surname)
        val email: TextView = view.findViewById(R.id.email)

        val progress = TintedProgressBar(requireContext(), view as ViewGroup)
        progress.show()
        GlobalScope.launch(Dispatchers.IO) {
            val userRepository = ConnectionActivity.getUserRepository()
            val watcher = userRepository.watchUser(viewModel.selectedVendor!!.dbId)
            this@VendorInfoFragment.watcher = watcher

            withContext(Dispatchers.Main) {
                watcher.getData().observe(viewLifecycleOwner) { res ->
                    progress.hide()
                    progress.remove()
                    val user = res.getOrShowError(requireContext()) ?: return@observe

                    fName.text = user.fName
                    sName.text = if (user.sName.isNullOrBlank()) getString(R.string.str_none) else user.sName
                    surname.text = if (user.surName.isNullOrBlank()) getString(R.string.str_none) else user.surName
                    email.text = user.login
                }
            }
        }
    }

    override fun onDestroy() {
        GlobalScope.launch(Dispatchers.IO) {
            watcher?.close()
        }
        super.onDestroy()
    }
}