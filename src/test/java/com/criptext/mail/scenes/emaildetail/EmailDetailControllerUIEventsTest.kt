package com.criptext.mail.scenes.emaildetail

import com.criptext.mail.R
import com.criptext.mail.db.models.Label
import com.criptext.mail.scenes.emaildetail.data.EmailDetailRequest
import com.criptext.mail.scenes.emaildetail.data.EmailDetailResult
import com.criptext.mail.scenes.emaildetail.ui.EmailDetailUIObserver
import com.criptext.mail.scenes.emaildetail.ui.FullEmailListAdapter
import com.criptext.mail.scenes.mailbox.OnMoveThreadsListener
import com.criptext.mail.utils.UIMessage
import io.mockk.*
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be instance of`
import org.amshove.kluent.`should contain`
import org.junit.Before
import org.junit.Test

class EmailDetailControllerUIEventsTest: EmailDetailControllerTest(){

    private val emailDetailUIObserverSlot = CapturingSlot<EmailDetailUIObserver>()
    private val fullEmailEventListener = CapturingSlot<FullEmailListAdapter.OnFullEmailEventListener>()
    private lateinit var listenerSlot: CapturingSlot<(EmailDetailResult) -> Unit>

    @Before
    override fun setUp() {
        super.setUp()
        every {
            scene.attachView(capture(fullEmailEventListener), any(), any(), capture(emailDetailUIObserverSlot), true, activeAccount, true)
        } just Runs

        listenerSlot = CapturingSlot()
        every {
            dataSource::listener.set(capture(listenerSlot))
        } just Runs
    }

    private fun simulateDownloadEvent(result: EmailDetailResult.DownloadFile) {
        listenerSlot.captured(result)
    }

    private fun simulateLoadOfEmails(size: Int) {
        listenerSlot.captured(EmailDetailResult.LoadFullEmailsFromThreadId.Success(createEmailItemsInThread(size), 2, null))
    }

    @Test
    fun `after clicking option to move to spam, should send MoveEmailThread request`() {

        controller.onStart(null)
        sentRequests.clear() // ignore requests sent during `onStart`()

        // capture onMoveThreadsListener
        val onMoveThreadsListenerSlot = CapturingSlot<OnMoveThreadsListener>()
        every {
            scene.showDialogMoveTo(capture(onMoveThreadsListenerSlot), Label.LABEL_INBOX)
        } just Runs

        controller.onOptionsItemSelected(R.id.mailbox_move_to)
        //trigger click move to spam
        onMoveThreadsListenerSlot.captured.onMoveToSpamClicked()

        val sentRequest = sentRequests.first()
        sentRequest `should be instance of` EmailDetailRequest.MoveEmailThread::class.java
    }

    @Test
    fun `Loaded emails should be added to model`() {
        controller.onStart(null)
        sentRequests.clear()
        simulateLoadOfEmails(2)

        model.emails.size `should be equal to` 2
    }

    @Test
    fun `clicking an attachment should trigger download worker`() {
        controller.onStart(null)
        sentRequests.clear()
        simulateLoadOfEmails(2)

        val selectedIndex = 0
        val selectedFile = model.emails[selectedIndex].files[selectedIndex]
        fullEmailEventListener.captured.onAttachmentSelected(0, 0)

        verify { dataSource.submitRequest(EmailDetailRequest.DownloadFile(fileName = selectedFile.name,
                fileSize = selectedFile.size, fileKey = "__FILE_KEY__", emailId = selectedFile.emailId,
                fileToken = selectedFile.token)) }
    }

    @Test
    fun `download progress should be set to respective file`() {
        controller.onStart(null)
        sentRequests.clear()
        simulateLoadOfEmails(2)
        val selectedIndex = 0
        val selectedEmail = model.emails[selectedIndex]
        val selectedFile = model.emails[selectedIndex].files[selectedIndex]

        simulateDownloadEvent(EmailDetailResult.DownloadFile.Progress(selectedFile.emailId, selectedFile.token, 50))
        model.fileDetails[selectedEmail.email.id]!![selectedIndex].progress `should be equal to` 50

        verify { scene.updateAttachmentProgress(selectedIndex + 1, selectedIndex) }
    }

    @Test
    fun `download success should call launch activity`() {
        controller.onStart(null)
        sentRequests.clear()

        simulateDownloadEvent(EmailDetailResult.DownloadFile.Success(1L,
                "__FILE_TOKEN__", "/test.pdf", null))
        verify { host.launchExternalActivityForResult(any()) }
    }

    @Test
    fun `download failure should display error message`() {
        controller.onStart(null)
        sentRequests.clear()
        simulateLoadOfEmails(2)
        val selectedIndex = 0
        val selectedFile = model.emails[selectedIndex].files[selectedIndex]

        simulateDownloadEvent(EmailDetailResult.DownloadFile.Failure(model.emails[selectedIndex].email.id,
                selectedFile.token, UIMessage(R.string.error_downloading_file, arrayOf(selectedFile.name))))
        verify { scene.showError(UIMessage(R.string.error_downloading_file, arrayOf(selectedFile.name))) }
    }

    @Test
    fun `after clicking star icon (ON), should send UpdateEmailThreadsLabelsRelations request`() {

        controller.onStart(null)
        sentRequests.clear()
        simulateLoadOfEmails(2)

        //The actual value of starred icon is ON
        fullEmailEventListener.captured.onStarredButtonPressed(isStarred = true)

        val sentRequest = sentRequests.last() as EmailDetailRequest.UpdateEmailThreadsLabelsRelations

        sentRequest.selectedLabels.toIDs() `should contain` Label.defaultItems.starred.id
    }

    @Test
    fun `after clicking star icon (OFF), should send UpdateEmailThreadsLabelsRelations request`() {

        controller.onStart(null)
        sentRequests.clear()
        simulateLoadOfEmails(2)

        //The actual value of starred icon is ON
        fullEmailEventListener.captured.onStarredButtonPressed(isStarred = false)

        val sentRequest = sentRequests.last() as EmailDetailRequest.UpdateEmailThreadsLabelsRelations

        sentRequest.selectedLabels.toIDs().`should be empty`()
    }
}