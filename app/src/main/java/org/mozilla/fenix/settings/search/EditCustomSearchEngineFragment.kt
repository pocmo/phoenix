package org.mozilla.fenix.settings.search

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.custom_search_engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.search.SearchEngine
import org.mozilla.fenix.R
import org.mozilla.fenix.components.FenixSnackbar
import org.mozilla.fenix.components.searchengine.CustomSearchEngineStore
import org.mozilla.fenix.ext.requireComponents
import java.util.Locale

class EditCustomSearchEngineFragment : Fragment() {
    private val safeArguments get() = requireNotNull(arguments)
    private val engineIdentifier: String by lazy {
        EditCustomSearchEngineFragmentArgs.fromBundle(
            safeArguments
        ).searchEngineIdentifier
    }

    private lateinit var searchEngine: SearchEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        searchEngine = CustomSearchEngineStore.loadCustomSearchEngines(requireContext()).first {
            it.identifier == engineIdentifier
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_search_engine, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        edit_engine_name.setText(searchEngine.name)
        val decodedUrl = Uri.decode(searchEngine.buildSearchUrl("%s"))
        edit_search_string.setText(decodedUrl)
    }

    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity).title = getString(R.string.search_engine_edit_custom_search_engine_title)
        (activity as AppCompatActivity).supportActionBar?.show()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.edit_custom_searchengine_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.save_button -> {
                saveCustomEngine()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveCustomEngine() {
        custom_search_engine_name_field.error = ""
        custom_search_engine_search_string_field.error = ""

        val name = edit_engine_name.text?.toString() ?: ""
        val searchString = edit_search_string.text?.toString() ?: ""

        var hasError = false
        if (name.isEmpty()) {
            custom_search_engine_name_field.error = resources
                .getString(R.string.search_add_custom_engine_error_empty_name)
            hasError = true
        }

        val existingIdentifiers = requireComponents
            .search
            .provider
            .allSearchEngineIdentifiers()
            .map { it.toLowerCase(Locale.ROOT) }

        val nameHasChanged = name != engineIdentifier

        if (existingIdentifiers.contains(name.toLowerCase(Locale.ROOT)) && nameHasChanged) {
            custom_search_engine_name_field.error = resources
                .getString(R.string.search_add_custom_engine_error_existing_name, name)
            hasError = true
        }

        if (searchString.isEmpty()) {
            custom_search_engine_search_string_field
                .error = resources.getString(R.string.search_add_custom_engine_error_empty_search_string)
            hasError = true
        }

        if (!searchString.contains("%s")) {
            custom_search_engine_name_field
                .error = resources.getString(R.string.search_add_custom_engine_error_missing_template)
            hasError = true
        }

        if (hasError) { return }

        viewLifecycleOwner.lifecycleScope.launch {
            val result = SearchStringValidator.isSearchStringValid(searchString)

            launch(Dispatchers.Main) {
                when (result) {
                    SearchStringValidator.Result.MalformedURL -> {
                        custom_search_engine_search_string_field.error = "Malformed URL"
                    }
                    SearchStringValidator.Result.CannotReach -> {
                        custom_search_engine_search_string_field.error = "Cannot Reach"
                    }
                    SearchStringValidator.Result.Success -> {
                        CustomSearchEngineStore.updateSearchEngine(
                            context = requireContext(),
                            oldEngineName = engineIdentifier,
                            newEngineName = name,
                            searchQuery = searchString
                        )
                        requireComponents.search.provider.reload()
                        val successMessage = resources
                            .getString(R.string.search_edit_custom_engine_success_message, name)

                        view?.also {
                            FenixSnackbar.make(it, FenixSnackbar.LENGTH_SHORT)
                                .setText(successMessage)
                                .show()
                        }

                        findNavController().popBackStack()
                    }
                }
            }
        }
    }
}
