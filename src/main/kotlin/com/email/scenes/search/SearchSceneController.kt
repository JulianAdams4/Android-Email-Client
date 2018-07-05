package com.email.scenes.search

import com.email.IHostActivity
import com.email.db.KeyValueStorage
import com.email.db.models.ActiveAccount
import com.email.db.models.Label
import com.email.email_preview.EmailPreview
import com.email.scenes.ActivityMessage
import com.email.scenes.SceneController
import com.email.scenes.composer.data.ComposerType
import com.email.scenes.mailbox.MailboxSceneController
import com.email.scenes.mailbox.data.EmailThread
import com.email.scenes.mailbox.data.LoadParams
import com.email.scenes.params.ComposerParams
import com.email.scenes.params.EmailDetailParams
import com.email.scenes.search.data.SearchDataSource
import com.email.scenes.search.data.SearchItem
import com.email.scenes.search.data.SearchRequest
import com.email.scenes.search.data.SearchResult
import com.email.scenes.search.ui.SearchHistoryAdapter
import com.email.scenes.search.ui.SearchThreadAdapter
import com.email.scenes.search.ui.SearchUIObserver

/**
 * Created by danieltigse on 2/2/18.
 */

class SearchSceneController(private val scene: SearchScene,
                            private val model: SearchSceneModel,
                            private val host: IHostActivity,
                            private val activeAccount: ActiveAccount,
                            storage: KeyValueStorage,
                            private val dataSource: SearchDataSource)
    : SceneController(){

    private val searchHistoryManager = SearchHistoryManager(storage)

    private val searchListController = SearchResultListController(
            model, scene.searchListView, scene.threadsListView)

    override val menuResourceId: Int?
        get() = null

    override fun onOptionsItemSelected(itemId: Int) {

    }

    companion object {
        const val MAXIMUM_SEARCH_HISTORY = 10
        const val SEPARATOR = "#Cr1p3tx2018#"
    }

    private val dataSourceListener = { result: SearchResult ->
        when (result) {
            is SearchResult.SearchEmails -> onSearchEmails(result)
        }
    }

    private val onSearchResultListController = object: SearchHistoryAdapter.OnSearchEventListener {

        override fun onSearchSelected(searchItem: SearchItem) {
            scene.setSearchText(searchItem.subject)
        }

        override fun onApproachingEnd() {
        }

    }

    private val threadEventListener = object : SearchThreadAdapter.OnThreadEventListener {

        override fun onApproachingEnd() {
            val req = SearchRequest.SearchEmails(
                    queryText = model.queryText,
                    loadParams = LoadParams.NewPage(size = MailboxSceneController.threadsPerPage,
                    startDate = model.threads.lastOrNull()?.timestamp),
                    userEmail = activeAccount.userEmail)
            dataSource.submitRequest(req)
        }

        override fun onGoToMail(emailThread: EmailThread) {

            if(emailThread.totalEmails == 1 &&
                    emailThread.latestEmail.labels.contains(Label.defaultItems.draft)) {
                val type = ComposerType.Draft(draftId = emailThread.latestEmail.email.id,
                        threadPreview = EmailPreview.fromEmailThread(emailThread),
                        currentLabel = Label.defaultItems.inbox)
                return host.goToScene(ComposerParams(type), false)
            }
            dataSource.submitRequest(SearchRequest.UpdateUnreadStatus(
                    listOf(emailThread), false, Label.defaultItems.inbox))
            val params = EmailDetailParams(threadId = emailThread.threadId,
                    currentLabel = Label.defaultItems.inbox,
                    threadPreview = EmailPreview.fromEmailThread(emailThread))
            host.goToScene(params, false)
        }

        override fun onToggleThreadSelection(thread: EmailThread, position: Int) {

        }
    }

    private val observer = object :SearchUIObserver{

        override fun onBackButtonClicked() {
            host.finishScene()
        }

        override fun onInputTextChange(text: String) {
            model.queryText = text
            if(text.isNotBlank()) {
                val req = SearchRequest.SearchEmails(
                        queryText = text,
                        loadParams = LoadParams.Reset(size = MailboxSceneController.threadsPerPage),
                        userEmail = activeAccount.userEmail)
                dataSource.submitRequest(req)
            }
        }

        override fun onSearchButtonClicked(text: String) {
            searchHistoryManager.saveSearchHistory(text)
        }

    }

    override fun onStart(activityMessage: ActivityMessage?): Boolean {
        scene.attachView(
                searchHistoryList = VirtualSearchHistoryList(model),
                searchListener = onSearchResultListController,
                threadsList = VirtualSearchThreadList(model),
                threadListener = threadEventListener,
                observer = observer
        )

        val results = searchHistoryManager.getSearchHistory()
        searchListController.setHistorySearchList(results)

        dataSource.listener = dataSourceListener

        return false
    }

    override fun onStop() {

    }

    override fun onBackPressed(): Boolean {
        return true
    }

    private fun onSearchEmails(result: SearchResult.SearchEmails){
        when(result) {
            is SearchResult.SearchEmails.Success -> {
                if(model.queryText == result.queryText) {
                    val hasReachedEnd = result.emailThreads.size < MailboxSceneController.threadsPerPage
                    if (model.threads.isNotEmpty() && result.isReset)
                        searchListController.populateThreads(result.emailThreads)
                    else {
                        searchListController.appendAll(result.emailThreads, hasReachedEnd)
                    }

                }
            }
        }
    }

    override fun requestPermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    }
}