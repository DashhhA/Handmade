package com.market.handmades.ui.auth

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.market.handmades.AuthActivity
import com.market.handmades.R
import com.market.handmades.model.User
import com.market.handmades.model.UserRegistrationDTO
import com.market.handmades.model.UserRepository
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.utils.ConnectionActivity
import com.market.handmades.utils.MTextWatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewUserFragment: Fragment() {
    private val viewModel: NewUserViewModel by activityViewModels()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_new_user, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // maps user types to their representations for spinner
        val userTypes: Map<User.UserType, String> = mapOf(
                User.UserType.Customer to "Покупатель",
                User.UserType.Vendor to "Продавец"
        )

        // get references to UI elements
        val userType: Spinner = view.findViewById(R.id.account_types)
        val email: EditText = view.findViewById(R.id.new_usr_email)
        val fName: EditText = view.findViewById(R.id.new_usr_fname)
        val sName: EditText = view.findViewById(R.id.new_usr_sname)
        val surName: EditText = view.findViewById(R.id.new_usr_surname)
        val pass: EditText = view.findViewById(R.id.new_usr_pass)
        val confirm: EditText = view.findViewById(R.id.new_usr_confirm_pass)
        val create: Button = view.findViewById(R.id.new_usr_create)

        val userTypesAdapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item)
        userType.adapter = userTypesAdapter
        userTypesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        userTypesAdapter.addAll(userTypes.values)
        create.setOnClickListener {
            // TODO hash password
            val newUser = UserRegistrationDTO(
                    viewModel.fName,
                    if (viewModel.sName.isBlank()) null else viewModel.sName,
                    if (viewModel.surName.isBlank()) null else viewModel.surName,
                    viewModel.email,
                    viewModel.password,
                    userTypes.keys.toList()[userType.selectedItemPosition].dbName
            )
            val progressBar = TintedProgressBar(requireContext(), view as ViewGroup)

            GlobalScope.launch {
                val connection = ConnectionActivity.awaitConnection()
                val userRepository = UserRepository(connection)
                val result = userRepository.newUser(newUser)

                withContext(Dispatchers.Main) {
                    progressBar.hide()
                    result.getOrShowError(requireContext()) ?: return@withContext

                    AuthUtils.logIn(
                            newUser.password,
                            newUser.login,
                            view,
                            requireContext(),
                            connection
                    ) {
                        requireActivity().finish()
                    }
                }
            }
        }

        // bind UI elements to ViewModel elements
        viewModel.bind(email, fName, sName, surName, userType, pass, confirm)
        viewModel.fNameError.observe(this.viewLifecycleOwner, {
            if (it != null) fName.error = getString(it)
        })
        viewModel.emailError.observe(this.viewLifecycleOwner, {
            if (it != null) email.error = getString(it)
        })
        viewModel.passwordError.observe(this.viewLifecycleOwner, {
            if (it != null) pass.error = getString(it)
            else confirm.error = null
        })
        viewModel.confirmPasswordError.observe(this.viewLifecycleOwner, {
            if (it != null) confirm.error = getString(it)
            else confirm.error = null
        })
        viewModel.enabledState.observe(this.viewLifecycleOwner, {
            create.isEnabled = it
        })

        // set UI state according to ViewModel
        email.setText(viewModel.email)
        fName.setText(viewModel.fName)
        sName.setText(viewModel.sName)
        surName.setText(viewModel.surName)
        pass.setText(viewModel.password)
        confirm.setText(viewModel.passwordConf)
        userType.setSelection(viewModel.userType)
    }
}

class NewUserViewModel: ViewModel() {
    var fName = ""
    var sName = ""
    var surName = ""
    var email = ""
    var passwordConf = ""
    var password = ""
    var userType = 0
    val fNameError = MutableLiveData<Int?>()
    val passwordError = MutableLiveData<Int?>()
    val emailError = MutableLiveData<Int?>()
    val confirmPasswordError = MutableLiveData<Int?>()
    val enabledState = MutableLiveData(false)

    private var nicknameValid = false
    private var emailValid = false
    private var passwordValid = false
    private var passwordConfirmed = false

    private val EMPTY_FIELD_ERROR = R.string.new_user_empty_error
    private val PASSWORD_TOO_SHORT = R.string.new_user_password_error
    private val PASSWORDS_MISMATCH = R.string.error_passwords_mismatch
    private val INVALID_EMAIL = R.string.error_email

    private val emailWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            email = s.toString()
            emailValid = validateEmail(email)
            updateEnabled()
        }
    }

    private val nicknameWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            fName = s.toString()
            nicknameValid = validateNickname(fName)
            updateEnabled()
        }
    }

    private val passwordWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            password = s.toString()
            passwordValid = validatePassword(password)
            passwordConfirmed = validateConfirmPassword(passwordConf)
            updateEnabled()
        }
    }

    private val confirmWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            passwordConf = s.toString()
            passwordValid = validatePassword(password)
            passwordConfirmed = validateConfirmPassword(passwordConf)
            updateEnabled()
        }
    }

    fun bind(
            email: EditText,
            fName: EditText,
            sName: EditText,
            surName: EditText,
            userType: Spinner,
            password: EditText,
            passwordConf: EditText
    ) {
        email.addTextChangedListener(emailWatcher)
        fName.addTextChangedListener(nicknameWatcher)
        password.addTextChangedListener(passwordWatcher)
        passwordConf.addTextChangedListener(confirmWatcher)
        sName.addTextChangedListener(MTextWatcher { this.sName = it })
        surName.addTextChangedListener(MTextWatcher { this.surName = it })
        userType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                this@NewUserViewModel.userType = position
            }

            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }

        emailValid = validateEmail(email.toString())
        nicknameValid = validateNickname(fName.toString())
        passwordValid = validatePassword(password.toString())
        passwordConfirmed = validateConfirmPassword(passwordConf.toString())
        updateEnabled()
    }

    private fun validateNickname(nickname: String): Boolean {
        return if (nickname.trim() == "") {
            fNameError.value = EMPTY_FIELD_ERROR
            false
        } else {
            fNameError.value = null
            true
        }
    }

    private fun validateEmail(email: String): Boolean {
        return if (email.trim() == "") {
            emailError.value = EMPTY_FIELD_ERROR
            false
        } else if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError.value = INVALID_EMAIL
            false
        }else {
            emailError.value = null
            true
        }
    }

    private fun validatePassword(password: String): Boolean {
        return if (password == "") {
            passwordError.value = EMPTY_FIELD_ERROR
            false
        } else if (password.length < 5) {
            passwordError.value = PASSWORD_TOO_SHORT
            false
        } else {
            passwordError.value = null
            true
        }
    }

    private fun validateConfirmPassword(confirm: String): Boolean {
        return if (confirm != password) {
            confirmPasswordError.value = PASSWORDS_MISMATCH
            false
        } else {
            confirmPasswordError.value = null
            true
        }
    }

    /**
     * Updates enabled state of the whole input sequence
     */
    private fun updateEnabled() {
        enabledState.value = nicknameValid && emailValid && passwordValid && passwordConfirmed
    }
}