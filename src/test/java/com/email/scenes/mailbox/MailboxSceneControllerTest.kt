package com.email.scenes.mailbox

import com.email.IHostActivity
import com.email.R
import com.email.api.ApiCall
import com.email.db.DeliveryTypes
import com.email.db.MailFolders
import com.email.db.MailboxLocalDB
import com.email.db.dao.EmailInsertionDao
import com.email.db.dao.signal.RawSessionDao
import com.email.db.models.*
import com.email.mocks.MockedJSONData
import com.email.mocks.MockedWorkRunner
import com.email.scenes.mailbox.data.*
import com.email.scenes.mailbox.feed.FeedController
import com.email.scenes.mailbox.ui.EmailThreadAdapter
import com.email.scenes.mailbox.ui.MailboxUIObserver
import com.email.signal.SignalClient
import com.email.websocket.WebSocketEventPublisher
import io.mockk.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be greater than`
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should equal`
import org.junit.Before
import org.junit.Test
import java.util.*

/**
 * Created by gabriel on 4/26/18.
 */

class MailboxSceneControllerTest {
    private lateinit var model: MailboxSceneModel
    private lateinit var scene: MailboxScene
    private lateinit var signal: SignalClient
    private lateinit var db: MailboxLocalDB
    private lateinit var rawSessionDao: RawSessionDao
    private lateinit var emailInsertionDao: EmailInsertionDao
    private lateinit var api: MailboxAPIClient
    private lateinit var runner: MockedWorkRunner
    private lateinit var dataSource: MailboxDataSource
    private lateinit var controller: MailboxSceneController
    private lateinit var host: IHostActivity
    private lateinit var webSocketEvents: WebSocketEventPublisher
    private lateinit var feedController : FeedController
    private lateinit var server : MockWebServer

    private val threadEventListenerSlot = CapturingSlot<EmailThreadAdapter.OnThreadEventListener>()
    private val onDrawerMenuEventListenerSlot = CapturingSlot<DrawerMenuItemListener>()
    private val observerSlot = CapturingSlot<MailboxUIObserver>()

    @Before
    fun setUp() {
        model = MailboxSceneModel()
        // mock MailboxScene capturing the thread event listener
        scene = mockk(relaxed = true)
        every {
            scene.attachView(MailFolders.INBOX, capture(threadEventListenerSlot),
                    capture(onDrawerMenuEventListenerSlot), capture(observerSlot), any())
        } just Runs

        runner = MockedWorkRunner()
        db = mockk(relaxed = true)
        rawSessionDao = mockk()

        emailInsertionDao = mockk(relaxed = true)
        val runnableSlot = CapturingSlot<Runnable>() // run transactions as they are invoked
        every { emailInsertionDao.runTransaction(capture(runnableSlot)) } answers {
            runnableSlot.captured.run()
        }

        api = MailboxAPIClient("__JWT_TOKEN")
        signal = mockk()

        host = mockk()

        dataSource = MailboxDataSource(
                runner = runner,
                signalClient = signal,
                mailboxLocalDB = db,
                activeAccount = ActiveAccount("gabriel", "__JWT_TOKEN__"),
                rawSessionDao = rawSessionDao,
                emailInsertionDao = emailInsertionDao
        )

        feedController = mockk(relaxed = true)
        webSocketEvents = mockk(relaxed = true)
        controller = MailboxSceneController(
                model =  model,
                scene = scene,
                dataSource = dataSource,
                host =  host,
                feedController = feedController,
                websocketEvents = webSocketEvents
        )

        server = MockWebServer()
        ApiCall.baseUrl = server.url("v1/mock").toString()
    }


    @Test
    fun `should forward onStart and onStop to FeedController`() {
        controller.onStart(null)
        controller.onStop()

        verifySequence {
            feedController.onStart()
            feedController.onStop()
        }
    }

    fun afterFirstLoad(assertions: () -> Unit) {
        controller.onStart(null)

        every {
            db.getEmailsFromMailboxLabel(labelTextTypes = MailFolders.INBOX,
                    oldestEmailThread = null,
                    rejectedLabels = any(),
                    limit = 20)
        } returns MailboxTestUtils.createEmailThreads(20)

        runner.assertPendingWork(listOf(GetMenuInformationWorker::class.java,
                LoadEmailThreadsWorker::class.java))
        runner._work()
        runner._work()
        assertions()
    }

    @Test
    fun `on a cold start should load threads from db and attempt to fetch events from server`() {
        afterFirstLoad {
            model.threads.size `should equal` 20

            runner.assertPendingWork(listOf(UpdateMailboxWorker::class.java))
        }
    }

    @Test
    fun `on a cold start should update lastSync after fetching events from server`() {
        afterFirstLoad {
            model.threads.size `should equal` 20

            // fetch an empty array of events
            server.enqueue(MockResponse().setResponseCode(404))

            runner.assertPendingWork(listOf(UpdateMailboxWorker::class.java))
            runner._work()

            model.lastSync `should be greater than` 0L
        }
    }
    @Test
    fun `if synced recently, should not fetch events after loading first thread page`() {
        // assume it synced recently
        model.lastSync = System.currentTimeMillis()
        afterFirstLoad {
            runner.assertPendingWork(emptyList())
        }
    }

    @Test
    fun `onStart, should not try to load threads if already synced and is not empty`() {
        // set as already synced
        model.lastSync = System.currentTimeMillis()
        // set as empty
        model.threads.addAll(MailboxTestUtils.createEmailThreads(20))

        controller.onStart(null)

        runner.assertPendingWork(listOf(GetMenuInformationWorker::class.java))
    }

    @Test
    fun `after clicking a navigation label, should clear threads list and load new ones`() {
        afterFirstLoad {
            // assume there is no pending work
            runner.discardPendingWork()

            // trigger ui event
            onDrawerMenuEventListenerSlot.captured.onNavigationItemClick(NavigationMenuOptions.TRASH)

            model.selectedLabel `should equal` Label.defaultItems.trash
            model.threads.`should be empty`()

            runner.assertPendingWork(listOf(LoadEmailThreadsWorker::class.java))
        }
    }

    @Test
    fun `pulling down should force mailbox to update`() {
        var didInsertSender = false

        // prepare mocks
        every {
            signal.decryptMessage("mayer", 1, "__ENCRYPTED_TEXT__")
        } returns "__PLAIN_TEXT__"
        every { emailInsertionDao.findContactsByEmail(listOf("mayer@jigl.com")) } returns emptyList()
        every {
            emailInsertionDao.findContactsByEmail(listOf("gabriel@jigl.com"))
            } returns listOf(Contact(id = 0, email ="gabriel@jigl.com", name = "Gabriel Aumala"))
        every {
            emailInsertionDao.insertContacts(listOf(Contact(0, "mayer@jigl.com",
                    "Mayer Mizrachi")))
        } answers { didInsertSender = true; listOf(2) }
        every {
            emailInsertionDao.findEmailByMessageId(any())
        } returns null

        // mock server responses
        val getEventsOneNewEmailResponse = "[${MockedJSONData.sampleNewEmailEvent}]"
        server.enqueue(MockResponse()
                .setBody(getEventsOneNewEmailResponse)
                .setResponseCode(200))
        server.enqueue(MockResponse()
                .setBody("__ENCRYPTED_TEXT__")
                .setResponseCode(200))


        afterFirstLoad {
            // assume there is no pending work
            runner.discardPendingWork()

            observerSlot.captured.onRefreshMails() // trigger pull down to refresh

            runner.assertPendingWork(listOf(UpdateMailboxWorker::class.java))
            runner._work()

            // final assertions
            verify { emailInsertionDao.insertEmail(assert("should have inserted new mail",
                    { e -> e.subject == "hello" && e.content == "__PLAIN_TEXT__"}))
            }
            verify(exactly = 2) {
                db.getEmailsFromMailboxLabel(labelTextTypes = MailFolders.INBOX,
                        oldestEmailThread = null,
                        rejectedLabels = any(),
                        limit = 20)
            }
            didInsertSender `should be` true
        }
    }

    @Test
    fun `after clicking delete, should bulk delete selected threads and then update UI`() {

        afterFirstLoad {
            // assume there is no pending work
            runner.discardPendingWork()

            model.threads.size `should equal` 20

            val threadEventListener = threadEventListenerSlot.captured

            //Select 3 threads
            val initialEmailThreads = model.threads.toList()
            threadEventListener.onToggleThreadSelection(initialEmailThreads[0], 0)
            threadEventListener.onToggleThreadSelection(initialEmailThreads[2], 10)

            model.selectedThreads.length() `should equal` 2

            //trigger bulk move to trash event
            controller.onOptionsItemSelected(R.id.mailbox_delete_selected_messages)

            runner.assertPendingWork(listOf(UpdateEmailThreadsLabelsRelationsWorker::class.java))
            runner._work()

            model.selectedThreads.length() `should equal` 0

            //Verify that only remove 2 relations and create 2 relations
            val emailIdsSlot = CapturingSlot<List<Long>>()
            val emailLabelsSlot = CapturingSlot<List<EmailLabel>>()
            verify {
                db.deleteRelationByEmailIds(capture(emailIdsSlot))
                db.createLabelEmailRelations(capture(emailLabelsSlot))
            }

            val emailIds = emailIdsSlot.captured
            val emailLabels = emailLabelsSlot.captured
            emailIds.size `should be` 2
            emailLabels.size `should be` 2

        }

    }

    @Test
    fun `after clicking spam, should bulk move to spam selected threads and then update UI`() {

        afterFirstLoad {
            // assume there is no pending work
            runner.discardPendingWork()

            model.threads.size `should equal` 20

            val threadEventListener = threadEventListenerSlot.captured

            //Select 3 threads
            val initialEmailThreads = model.threads.toList()
            threadEventListener.onToggleThreadSelection(initialEmailThreads[0], 0)
            threadEventListener.onToggleThreadSelection(initialEmailThreads[2], 10)

            model.selectedThreads.length() `should equal` 2

            //trigger bulk move to trash event
            controller.onOptionsItemSelected(R.id.mailbox_move_to)

            val onMoveThreadsListenerSlot = CapturingSlot<OnMoveThreadsListener>()
            verify {
                scene.showDialogMoveTo(capture(onMoveThreadsListenerSlot))
            }

            onMoveThreadsListenerSlot.captured.moveToSpam()

            runner.assertPendingWork(listOf(UpdateEmailThreadsLabelsRelationsWorker::class.java))
            runner._work()

            model.selectedThreads.length() `should equal` 0

            //Verify that only remove 2 relations and create 2 relations
            val emailIdsSlot = CapturingSlot<List<Long>>()
            val emailLabelsSlot = CapturingSlot<List<EmailLabel>>()
            verify {
                db.deleteRelationByEmailIds(capture(emailIdsSlot))
                db.createLabelEmailRelations(capture(emailLabelsSlot))
            }

            val emailIds = emailIdsSlot.captured
            val emailLabels = emailLabelsSlot.captured
            emailIds.size `should be` 2
            emailLabels.size `should be` 2

        }

    }
}