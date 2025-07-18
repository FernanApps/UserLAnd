package tech.ula.ui

import android.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.navArgs
import tech.ula.MainActivity
import tech.ula.R
import tech.ula.databinding.FragFilesystemEditBinding
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.PermissionHandler
import tech.ula.utils.CredentialValidator
import tech.ula.utils.UlaFiles
import tech.ula.utils.preferences.AppsPreferences
import tech.ula.viewmodel.FilesystemImportStatus
import tech.ula.viewmodel.ImportSuccess
import tech.ula.viewmodel.ImportFailure
import tech.ula.viewmodel.FilesystemEditViewModel
import tech.ula.viewmodel.FilesystemEditViewmodelFactory
import tech.ula.viewmodel.UriUnselected
import java.util.Locale

class FilesystemEditFragment : Fragment() {

    private var _binding: FragFilesystemEditBinding? = null
    private val binding get() = _binding!!

    private lateinit var activityContext: MainActivity

    private val IMPORT_FILESYSTEM_REQUEST_CODE = 5

    private val args: FilesystemEditFragmentArgs by navArgs()
    private val filesystem by lazy { args.filesystem!! }
    private val editExisting by lazy { args.editExisting }

    private val filesystemImportStatusObserver = Observer<FilesystemImportStatus> {
        it?.let { importStatus ->
            val dialogBuilder = AlertDialog.Builder(activityContext)
            when (importStatus) {
                is ImportSuccess -> dialogBuilder.setMessage(R.string.import_success).create().show()
                is ImportFailure -> dialogBuilder.setMessage(R.string.import_failure).create().show()
                UriUnselected ->  dialogBuilder.setMessage(R.string.import_failure).create().show()
            }
        }
    }

    private val filesystemEditViewModel: FilesystemEditViewModel by lazy {
        val ulaDatabase = UlaDatabase.getInstance(activityContext)
        ViewModelProviders.of(this, FilesystemEditViewmodelFactory(ulaDatabase)).get(FilesystemEditViewModel::class.java)
    }

    private val distributionList by lazy {
        AppsPreferences(activityContext).getDistributionsList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_edit, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.menu_item_add) insertFilesystem()
        else super.onOptionsItemSelected(item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragFilesystemEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activityContext = requireActivity() as MainActivity
        filesystemEditViewModel.getImportStatusLiveData().observe(viewLifecycleOwner, filesystemImportStatusObserver)

        if (distributionList.isNotEmpty()) {
            binding.spinnerFilesystemType.adapter = ArrayAdapter(activityContext,
                    android.R.layout.simple_spinner_dropdown_item,
                    distributionList.map { it.capitalize() })
        }
        if (editExisting) {
            for (i in 0 until binding.spinnerFilesystemType.adapter.count) {
                val item = binding.spinnerFilesystemType.adapter.getItem(i).toString().toLowerCase(Locale.ENGLISH)
                if (item == filesystem.distributionType) binding.spinnerFilesystemType.setSelection(i)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTextInputs()

        if (editExisting) {
            binding.btnShowAdvancedOptions.visibility = View.GONE
            binding.spinnerFilesystemType.isEnabled = false
        } else {
            setupImportButton()
            setupAdvancedOptionButton()
        }
        binding.spinnerFilesystemType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filesystem.distributionType = parent?.getItemAtPosition(position).toString().toLowerCase(Locale.ENGLISH)
            }
        }
    }

    /*private fun setupTextInputs() {
        input_filesystem_name.setText(filesystem.name)
        input_filesystem_username.setText(filesystem.defaultUsername)
        input_filesystem_password.setText(filesystem.defaultPassword)
        input_filesystem_vncpassword.setText(filesystem.defaultVncPassword)

        if (editExisting) {
            input_filesystem_username.isEnabled = false
            input_filesystem_password.isEnabled = false
            input_filesystem_vncpassword.isEnabled = false
        }

        if (filesystem.isAppsFilesystem) {
            input_filesystem_name.isEnabled = false
        }

        input_filesystem_name.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                filesystem.name = p0.toString()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        input_filesystem_username.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {

                filesystem.defaultUsername = p0.toString()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        input_filesystem_password.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                filesystem.defaultPassword = p0.toString()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        input_filesystem_vncpassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                filesystem.defaultVncPassword = p0.toString()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })
    }*/

    private fun setupTextInputs() = with(binding) {
        inputFilesystemName.setText(filesystem.name)
        inputFilesystemUsername.setText(filesystem.defaultUsername)
        inputFilesystemPassword.setText(filesystem.defaultPassword)
        inputFilesystemVncpassword.setText(filesystem.defaultVncPassword)

        if (editExisting) {
            inputFilesystemUsername.isEnabled = false
            inputFilesystemPassword.isEnabled = false
            inputFilesystemVncpassword.isEnabled = false
        }

        if (filesystem.isAppsFilesystem) {
            inputFilesystemName.isEnabled = false
        }

        inputFilesystemName.addTextChangedListener { filesystem.name = it.toString() }
        inputFilesystemUsername.addTextChangedListener { filesystem.defaultUsername = it.toString() }
        inputFilesystemPassword.addTextChangedListener { filesystem.defaultPassword = it.toString() }
        inputFilesystemVncpassword.addTextChangedListener { filesystem.defaultVncPassword = it.toString() }
    }

    private fun setupImportButton() {
        binding.importButton.setOnClickListener {
            val filePickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            filePickerIntent.addCategory(Intent.CATEGORY_OPENABLE)
            filePickerIntent.type = "application/*"
            if (!PermissionHandler.permissionsAreGranted(activityContext)) {
                PermissionHandler.showPermissionsNecessaryDialog(activityContext)
                return@setOnClickListener
            }

            try {
                filesystem.isCreatedFromBackup = true
                startActivityForResult(filePickerIntent, IMPORT_FILESYSTEM_REQUEST_CODE)
            } catch (activityNotFoundErr: ActivityNotFoundException) {
                Toast.makeText(activityContext, R.string.prompt_install_file_manager, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupAdvancedOptionButton() {
        val btn = binding.btnShowAdvancedOptions

        btn.setOnClickListener {
            when (btn.isChecked) {
                true -> {
                    btn.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_keyboard_arrow_down_white_24dp, 0)
                    binding.advancedOptions.visibility = View.VISIBLE
                }
                false -> {
                    btn.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_keyboard_arrow_right_white_24dp, 0)
                    binding.advancedOptions.visibility = View.INVISIBLE
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, returnIntent: Intent?) {
        super.onActivityResult(requestCode, resultCode, returnIntent)
        if (requestCode == IMPORT_FILESYSTEM_REQUEST_CODE) {
            returnIntent?.data?.let { uri ->
                filesystemEditViewModel.backupUri = uri
                binding.textBackupFilename.text = uri.lastPathSegment
            }
        }
    }

    private fun insertFilesystem(): Boolean {
        val navController = NavHostFragment.findNavController(this)
        if (!filesystemParametersAreCorrect()) {
            return false
        }

        if (editExisting) {
            filesystemEditViewModel.updateFilesystem(filesystem)
            navController.popBackStack()
        } else {
            val ulaFiles = UlaFiles(activityContext, activityContext.applicationInfo.nativeLibraryDir)
            filesystem.archType = ulaFiles.getArchType()
            if (filesystem.isCreatedFromBackup) {
                filesystemEditViewModel.insertFilesystemFromBackup(activityContext.contentResolver, filesystem, activityContext.filesDir)
            } else {
                filesystemEditViewModel.insertFilesystem(filesystem)
            }
            navController.popBackStack()
        }

        return true
    }

    private fun filesystemParametersAreCorrect(): Boolean {
        val blacklistedUsernames = activityContext.resources.getStringArray(R.array.blacklisted_usernames)
        val validator = CredentialValidator()
        val filesystemName = filesystem.name
        val username = filesystem.defaultUsername
        val password = filesystem.defaultPassword
        val vncPassword = filesystem.defaultVncPassword

        val filesystemNameCredentials = validator.validateFilesystemName(filesystemName)
        val usernameCredentials = validator.validateUsername(username, blacklistedUsernames)
        val passwordCredentials = validator.validatePassword(password)
        val vncPasswordCredentials = validator.validateVncPassword(vncPassword)

        when {
            !filesystemNameCredentials.credentialIsValid ->
                Toast.makeText(activityContext, filesystemNameCredentials.errorMessageId, Toast.LENGTH_LONG).show()
            !usernameCredentials.credentialIsValid ->
                Toast.makeText(activityContext, usernameCredentials.errorMessageId, Toast.LENGTH_LONG).show()
            !passwordCredentials.credentialIsValid ->
                Toast.makeText(activityContext, passwordCredentials.errorMessageId, Toast.LENGTH_LONG).show()
            !vncPasswordCredentials.credentialIsValid ->
                Toast.makeText(activityContext, vncPasswordCredentials.errorMessageId, Toast.LENGTH_LONG).show()
            else ->
                return true
        }
        return false
    }
}