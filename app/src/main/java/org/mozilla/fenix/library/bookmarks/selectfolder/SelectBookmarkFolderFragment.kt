/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.library.bookmarks.selectfolder

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.fragment_select_bookmark_folder.*
import kotlinx.android.synthetic.main.fragment_select_bookmark_folder.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mozilla.appservices.places.BookmarkRoot
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.sync.AccountObserver
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.concept.sync.Profile
import org.mozilla.fenix.BrowserDirection
import org.mozilla.fenix.FenixViewModelProvider
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.getColorFromAttr
import org.mozilla.fenix.ext.nav
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.library.bookmarks.BookmarksSharedViewModel
import org.mozilla.fenix.library.bookmarks.SignInAction
import org.mozilla.fenix.library.bookmarks.SignInChange
import org.mozilla.fenix.library.bookmarks.SignInComponent
import org.mozilla.fenix.library.bookmarks.SignInState
import org.mozilla.fenix.library.bookmarks.SignInViewModel
import org.mozilla.fenix.mvi.ActionBusFactory
import org.mozilla.fenix.mvi.getAutoDisposeObservable
import org.mozilla.fenix.mvi.getManagedEmitter
import kotlin.coroutines.CoroutineContext

@SuppressWarnings("TooManyFunctions")
class SelectBookmarkFolderFragment : Fragment(), CoroutineScope, AccountObserver {

    private lateinit var sharedViewModel: BookmarksSharedViewModel
    private lateinit var job: Job
    private var folderGuid: String? = null
    private var bookmarkNode: BookmarkNode? = null

    private lateinit var signInComponent: SignInComponent

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    // Map of internal "root titles" to user friendly labels.
    private lateinit var rootTitles: Map<String, String>

    // Fill out our title map once we have context.
    override fun onAttach(context: Context) {
        super.onAttach(context)

        rootTitles = mapOf(
            "root" to context.getString(R.string.library_desktop_bookmarks_root),
            "menu" to context.getString(R.string.library_desktop_bookmarks_menu),
            "toolbar" to context.getString(R.string.library_desktop_bookmarks_toolbar),
            "unfiled" to context.getString(R.string.library_desktop_bookmarks_unfiled)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()
        setHasOptionsMenu(true)
        sharedViewModel = activity?.run {
            ViewModelProviders.of(this).get(BookmarksSharedViewModel::class.java)
        }!!
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_select_bookmark_folder, container, false)
        signInComponent = SignInComponent(
            view.select_bookmark_layout,
            ActionBusFactory.get(this),
            FenixViewModelProvider.create(
                this,
                SignInViewModel::class.java
            ) {
                SignInViewModel(SignInState(false))
            }
        )
        return view
    }

    override fun onStart() {
        super.onStart()
        getAutoDisposeObservable<SignInAction>()
            .subscribe {
                when (it) {
                    is SignInAction.ClickedSignIn -> {
                        requireComponents.services.accountsAuthFeature.beginAuthentication(requireContext())
                        view?.let {
                            (activity as HomeActivity).openToBrowser(BrowserDirection.FromBookmarksFolderSelect)
                        }
                    }
                }
            }
    }

    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity).title =
            getString(R.string.bookmark_select_folder_fragment_label)
        (activity as AppCompatActivity).supportActionBar?.show()

        folderGuid = SelectBookmarkFolderFragmentArgs.fromBundle(arguments!!).folderGuid ?: BookmarkRoot.Root.id
        checkIfSignedIn()

        launch(IO) {
            bookmarkNode = withOptionalDesktopFolders(requireComponents.core.bookmarksStorage.getTree(folderGuid!!, true))
            launch(Main) {
                (activity as HomeActivity).title = bookmarkNode?.title ?: getString(R.string.library_bookmarks)
                val adapter = SelectBookmarkFolderAdapter(sharedViewModel)
                recylerView_bookmark_folders.adapter = adapter
                adapter.updateData(bookmarkNode)
            }
        }
    }

    private fun checkIfSignedIn() {
        val accountManager = requireComponents.backgroundServices.accountManager
        accountManager.register(this, owner = this)
        accountManager.authenticatedAccount()?.let { getManagedEmitter<SignInChange>().onNext(SignInChange.SignedIn) }
            ?: getManagedEmitter<SignInChange>().onNext(SignInChange.SignedOut)
    }

    private suspend fun virtualDesktopFolder(): BookmarkNode? {
        val rootNode = requireComponents.core.bookmarksStorage.getTree(BookmarkRoot.Root.id, false) ?: return null
        return BookmarkNode(
            type = rootNode.type,
            guid = rootNode.guid,
            parentGuid = rootNode.parentGuid,
            position = rootNode.position,
            title = rootTitles[rootNode.title],
            url = rootNode.url,
            children = rootNode.children
        )
    }

    @SuppressWarnings("ReturnCount")
    private suspend fun withOptionalDesktopFolders(node: BookmarkNode?): BookmarkNode? {
        // No-op if node is missing.
        if (node == null) {
            return null
        }

        // If we're in the mobile root, add-in a synthetic "Desktop Bookmarks" folder.
        if (node.guid == BookmarkRoot.Mobile.id) {
            // We're going to make a copy of the mobile node, and add-in a synthetic child folder to the top of the
            // children's list that contains all of the desktop roots.
            val childrenWithVirtualFolder: MutableList<BookmarkNode> = mutableListOf()
            virtualDesktopFolder()?.let { childrenWithVirtualFolder.add(it) }

            node.children?.let { children ->
                childrenWithVirtualFolder.addAll(children)
            }

            return BookmarkNode(
                type = node.type,
                guid = node.guid,
                parentGuid = node.parentGuid,
                position = node.position,
                title = node.title,
                url = node.url,
                children = childrenWithVirtualFolder
            )

            // If we're looking at the root, that means we're in the "Desktop Bookmarks" folder.
            // Rename its child roots and remove the mobile root.
        } else if (node.guid == BookmarkRoot.Root.id) {
            return BookmarkNode(
                type = node.type,
                guid = node.guid,
                parentGuid = node.parentGuid,
                position = node.position,
                title = rootTitles[node.title],
                url = node.url,
                children = processDesktopRoots(node.children)
            )
            // If we're looking at one of the desktop roots, change their titles to friendly names.
        } else if (node.guid in listOf(BookmarkRoot.Menu.id, BookmarkRoot.Toolbar.id, BookmarkRoot.Unfiled.id)) {
            return BookmarkNode(
                type = node.type,
                guid = node.guid,
                parentGuid = node.parentGuid,
                position = node.position,
                title = rootTitles[node.title],
                url = node.url,
                children = node.children
            )
        }

        // Otherwise, just return the node as-is.
        return node
    }

    /**
     * Removes 'mobile' root (to avoid a cyclical bookmarks tree in the UI) and renames other roots to friendly titles.
     */
    private fun processDesktopRoots(roots: List<BookmarkNode>?): List<BookmarkNode>? {
        if (roots == null) {
            return null
        }

        return roots.filter { rootTitles.containsKey(it.title) }.map {
            BookmarkNode(
                type = it.type,
                guid = it.guid,
                parentGuid = it.parentGuid,
                position = it.position,
                title = rootTitles[it.title],
                url = it.url,
                children = it.children
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val visitedAddBookmark = SelectBookmarkFolderFragmentArgs.fromBundle(arguments!!).visitedAddBookmark
        if (!visitedAddBookmark) {
            inflater.inflate(R.menu.bookmarks_select_folder, menu)
            menu.findItem(R.id.add_folder_button).icon.colorFilter =
                PorterDuffColorFilter(R.attr.primaryText.getColorFromAttr(context!!), PorterDuff.Mode.SRC_IN)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.add_folder_button -> {
                launch(Main) {
                    nav(
                        R.id.bookmarkSelectFolderFragment,
                        SelectBookmarkFolderFragmentDirections
                            .actionBookmarkSelectFolderFragmentToBookmarkAddFolderFragment()
                    )
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onAuthenticationProblems() {
    }

    override fun onAuthenticated(account: OAuthAccount) {
        getManagedEmitter<SignInChange>().onNext(SignInChange.SignedIn)
    }

    override fun onError(error: Exception) {
    }

    override fun onLoggedOut() {
        getManagedEmitter<SignInChange>().onNext(SignInChange.SignedOut)
    }

    override fun onProfileUpdated(profile: Profile) {
    }
}
