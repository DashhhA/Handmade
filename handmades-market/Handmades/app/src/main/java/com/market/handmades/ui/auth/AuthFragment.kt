package com.market.handmades.ui.auth

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.navigation.Navigation
import com.market.handmades.AuthActivity
import com.market.handmades.R
import com.market.handmades.remote.Connection
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class AuthFragment: Fragment() {
    private val viewModel: AuthViewModel by viewModels()
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_auth, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // get references to UI elements
        val email: EditText = view.findViewById(R.id.loginEmail)
        val pass: EditText = view.findViewById(R.id.loginPassword)
        // button to log in
        val submit: Button = view.findViewById(R.id.loginSubmit)
        // button for new account
        val newAcc: Button = view.findViewById(R.id.loginCreate)

        // assign actions to buttons
        val navigation = Navigation.findNavController(view)
        newAcc.setOnClickListener {
            navigation.navigate(R.id.action_authFragment_to_newUserFragment)
        }
        GlobalScope.launch(Dispatchers.Unconfined) {
            val connection = ConnectionActivity.awaitConnection()

            submit.setOnClickListener {
                AuthUtils.logIn(
                        pass.text.toString(),
                        viewModel.email,
                        view as ViewGroup,
                        requireContext(),
                        connection
                ) {
                    requireActivity().finish()
                }
            }
        }

        // bind UI elements to ViewModel elements
        viewModel.bind(email, pass)
        viewModel.emailError.observe(this.viewLifecycleOwner, {
            if (it != null) email.error = getString(it)
        })
        viewModel.passwordError.observe(this.viewLifecycleOwner, {
            if (it != null) pass.error = getString(it)
        })
        viewModel.enabledState.observe(this.viewLifecycleOwner, {
            submit.isEnabled = it
        })

        // set UI state according to ViewModel
        email.setText(viewModel.email)
    }
}

class AuthViewModel: ViewModel() {
    var email: String = ""
    val emailError = MutableLiveData<Int?>()
    val passwordError = MutableLiveData<Int?>()
    val enabledState = MutableLiveData(false)

    private var emailValid = false
    private var passwordValid = false

    private val EMPTY_FIELD_ERROR = R.string.new_user_empty_error
    private val PASSWORD_TOO_SHORT = R.string.new_user_password_error
    private val INVALID_EMAIL = R.string.error_email

    private val emailWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            emailValid = validateEmail(s.toString())
            email = s.toString()
            updateEnabled()
        }
    }

    private val passwordWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            passwordValid = validatePassword(s.toString())
            updateEnabled()
        }
    }

    /**
     * Binds change listeners to UI elements
     */
    fun bind(email: EditText, password: EditText) {
        email.addTextChangedListener(emailWatcher)
        password.addTextChangedListener(passwordWatcher)

        emailValid = validateEmail(email.text.toString())
        passwordValid = validatePassword(password.text.toString())
    }

    private fun validateEmail(email: String): Boolean {
        if (email.trim().equals("")) {
            emailError.value = EMPTY_FIELD_ERROR
            return false
        } else if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError.value = INVALID_EMAIL
            return false
        }else {
            emailError.value = null
            return true
        }
    }

    private fun validatePassword(password: String): Boolean {
        if (password.equals("")) {
            passwordError.value = EMPTY_FIELD_ERROR
            return false
        } else if (password.length < 5) {
            passwordError.value = PASSWORD_TOO_SHORT
            return false
        } else {
            passwordError.value = null
            return true
        }
    }

    /**
     * Updates enabled state of the whole input sequence
     */
    private fun updateEnabled() {
        enabledState.value = emailValid && passwordValid
    }
}