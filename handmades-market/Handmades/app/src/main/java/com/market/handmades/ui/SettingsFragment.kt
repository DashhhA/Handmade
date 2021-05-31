package com.market.handmades.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.market.handmades.AuthActivity
import com.market.handmades.R
import com.market.handmades.remote.ServerRequest
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SettingsFragment: Fragment() {
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnDelAcc: Button = view.findViewById(R.id.del_acc)
        btnDelAcc.setOnClickListener {
            AlertDialog.Builder(requireContext())
                    .setMessage(R.string.confirm_del_acc)
                    .setNegativeButton(R.string.str_cancel) { _, _, -> }
                    .setPositiveButton(R.string.str_yes) { _, _, -> deleteAccount(view as ViewGroup) }
                    .show()
        }
        val btnLogout: Button = view.findViewById(R.id.logout)
        btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.confirm_logout)
                .setNegativeButton(R.string.str_cancel) { _, _, -> }
                .setPositiveButton(R.string.str_yes) { _, _, -> logout() }
                .show()
        }
    }


    private fun deleteAccount(view: ViewGroup) {
        val progress = TintedProgressBar(requireContext(), view)
        progress.show()
        GlobalScope.launch(Dispatchers.IO) {
            val connection = ConnectionActivity.awaitConnection()
            val req = ServerRequest.RemoveUser()
            val res = connection.requestServer(req)

            progress.hide()

            res.getOrShowError(requireContext()) ?: return@launch

            val intent = Intent(requireActivity(), AuthActivity::class.java)
            startActivity(intent)
            requireActivity().finish()
        }
    }

    private fun logout() {
        GlobalScope.launch(Dispatchers.IO) {
            val connection = ConnectionActivity.awaitConnection()
            val res = connection.requestServer(ServerRequest.Logout())

            res.getOrShowError(requireContext()) ?: return@launch

            val intent = Intent(requireActivity(), AuthActivity::class.java)
            startActivity(intent)
            requireActivity().finish()
        }
    }
}