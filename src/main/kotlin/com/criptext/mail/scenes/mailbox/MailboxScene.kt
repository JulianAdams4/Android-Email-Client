package com.criptext.mail.scenes.mailbox

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.criptext.mail.ExternalActivityParams
import com.criptext.mail.IHostActivity
import com.criptext.mail.R
import com.criptext.mail.api.models.DeviceInfo
import com.criptext.mail.db.AccountTypes
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.models.Account
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.db.models.Label
import com.criptext.mail.scenes.label_chooser.LabelChooserDialog
import com.criptext.mail.scenes.label_chooser.LabelDataHandler
import com.criptext.mail.scenes.label_chooser.data.LabelWrapper
import com.criptext.mail.scenes.mailbox.data.UpdateBannerData
import com.criptext.mail.scenes.mailbox.holders.ToolbarHolder
import com.criptext.mail.scenes.mailbox.ui.*
import com.criptext.mail.scenes.mailbox.ui.WelcomeTour.WelcomeTourDialog
import com.criptext.mail.services.jobs.CloudBackupJobService
import com.criptext.mail.utils.*
import com.criptext.mail.utils.apputils.AppRater
import com.criptext.mail.utils.ui.*
import com.criptext.mail.utils.ui.data.DialogData
import com.criptext.mail.utils.ui.data.DialogType
import com.criptext.mail.utils.uiobserver.UIObserver
import com.criptext.mail.utils.virtuallist.VirtualListView
import com.criptext.mail.utils.virtuallist.VirtualRecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.navigation.NavigationView
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.android.synthetic.main.mail_item_left_name.view.*
import java.util.*

/**
 * Created by sebas on 1/23/18.
 */

interface MailboxScene{
    val virtualListView: VirtualListView
    var observer: MailboxUIObserver?
    var threadEventListener: EmailThreadAdapter.OnThreadEventListener?
    var onMoveThreadsListener: OnMoveThreadsListener?
    fun showSyncingDialog()
    fun hideSyncingDialog()
    fun initDrawerLayout()
    fun initNavHeader(fullName: String, email: String, accountType: AccountTypes)
    fun initMailboxAvatar(fullName: String, email: String, accountType: AccountTypes)
    fun showExtraAccountsBadge(show: Boolean)
    fun hideMultipleAccountsMenu()
    fun onBackPressed(): Boolean
    fun attachView(
            threadEventListener: EmailThreadAdapter.OnThreadEventListener,
            onDrawerMenuItemListener: DrawerMenuItemListener,
            observer: MailboxUIObserver,
            threadList: VirtualEmailThreadList, activeAccount: ActiveAccount)
    fun refreshToolbarItems()
    fun showMultiModeBar(selectedThreadsQuantity : Int)
    fun hideMultiModeBar()
    fun updateToolbarTitle(title: String)
    fun showDialogLabelsChooser(labelDataSourceHandler: LabelDataHandler)
    fun showDialogMoveTo(onMoveThreadsListener: OnMoveThreadsListener, currentFolder: String)
    fun showDialogDeleteThread(onDeleteThreadListener: OnDeleteThreadListener)
    fun showWelcomeDialog(observer: MailboxUIObserver)
    fun showRestoreBackupDialog(observer: MailboxUIObserver)
    fun setToolbarNumberOfEmails(emailsSize: Int)
    fun openNotificationFeed()
    fun onFetchedSelectedLabels(defaultSelectedLabels: List<Label>, labels: List<Label>)
    fun refreshMails()
    fun clearRefreshing()
    fun showMessage(message : UIMessage, showAction: Boolean = false)
    fun showToastMessage(message : UIMessage)
    fun hideDrawer()
    fun showRefresh()
    fun scrollTop()
    fun setCounterLabel(menu: NavigationMenuOptions, total: Int)
    fun updateBadges(badgeData: List<Pair<String, Int>>)
    fun setMenuLabels(labels: List<LabelWrapper>)
    fun setMenuAccounts(accounts: List<Account>, badgeCount: List<Int>)
    fun clearMenuActiveLabel()
    fun dismissAccountSuspendedDialog()
    fun showAccountSuspendedDialog(observer: UIObserver, email: String, dialogType: DialogType)
    fun showEmptyTrashBanner()
    fun hideEmptyTrashBanner()
    fun showUpdateBanner(bannerData: UpdateBannerData)
    fun hideUpdateBanner()
    fun setUpdateBannerClick()
    fun clearUpdateBannerClick()
    fun showEmptyTrashWarningDialog(onEmptyTrashListener: OnEmptyTrashListener)
    fun showLinkDeviceAuthConfirmation(untrustedDeviceInfo: DeviceInfo.UntrustedDeviceInfo)
    fun showSyncDeviceAuthConfirmation(trustedDeviceInfo: DeviceInfo.TrustedDeviceInfo)
    fun dismissLinkDeviceDialog()
    fun dismissSyncDeviceDialog()
    fun showSyncPhonebookDialog(observer: MailboxUIObserver)
    fun showStartGuideEmail()
    fun showStartGuideMultiple()
    fun showSecureLockGuide(position: Int)
    fun showNotification()
    fun setEmtpyMailboxBackground(label: Label)
    fun getGoogleDriveService(): Drive?
    fun showPreparingFileDialog()
    fun dismissPreparingFileDialog()
    fun hideComposer(shouldHide: Boolean)
    fun checkRating(storage: KeyValueStorage)
    fun showRecommendBackupDialog()
    fun scheduleCloudBackupJob(period: Int, accountId: Long, useWifiOnly: Boolean)
    fun removeFromScheduleCloudBackupJob(accountId: Long)
    fun showUpdateNowDialog()

    class MailboxSceneView(private val mailboxView: View, val hostActivity: IHostActivity)
        : MailboxScene {
        override fun showSyncingDialog() {
            hostActivity.showDialog(UIMessage(R.string.updating_mailbox))
        }

        override fun hideSyncingDialog() {
            hostActivity.dismissDialog()
        }

        private val context = mailboxView.context

        private lateinit var adapter: EmailThreadAdapter

        override var observer: MailboxUIObserver? = null

        override var threadEventListener: EmailThreadAdapter.OnThreadEventListener? = null

        override var onMoveThreadsListener: OnMoveThreadsListener? = null

        private val labelChooserDialog = LabelChooserDialog(context, mailboxView)
        private val moveToDialog = MoveToDialog(context)
        private val deleteDialog = DeleteThreadDialog(context)
        private val emptyTrashDialog = EmptyTrashDialog(context)
        private val welcomeDialog = WelcomeTourDialog(context)
        private val accountSuspended = AccountSuspendedDialog(context)
        private val linkAuthDialog = LinkNewDeviceAlertDialog(context)
        private val syncAuthDialog = SyncDeviceAlertDialog(context)
        private val syncPhonebookDialog = SyncPhonebookDialog(context)
        private val restoreBackupDialog = RestoreBackupDialog(context)
        private val preparingFileDialog = MessageAndProgressDialog(context, UIMessage(R.string.preparing_file))
        private val recommendBackupDialog = RecommendBackupDialog(context)

        private lateinit var drawerMenuView: DrawerMenuView

        private val recyclerView: RecyclerView by lazy {
            mailboxView.findViewById<RecyclerView>(R.id.mailbox_recycler)
        }

        private val refreshLayout: SwipeRefreshLayout by lazy {
            mailboxView.findViewById<SwipeRefreshLayout>(R.id.mailbox_refresher)
        }

        private val toolbarHolder: ToolbarHolder by lazy {
            val view = mailboxView.findViewById<Toolbar>(R.id.mailbox_toolbar)
            ToolbarHolder(view)
        }

        private val drawerLayout: DrawerLayout by lazy {
            mailboxView.findViewById<DrawerLayout>(R.id.drawer_layout)
        }

        private val leftNavigationView: NavigationView by lazy {
            mailboxView.findViewById<NavigationView>(R.id.nav_left_view)
        }

        private val rightNavigationView: NavigationView by lazy {
            mailboxView.findViewById<NavigationView>(R.id.nav_right_view)
        }

        private val navButton: CircleImageView by lazy {
            mailboxView.findViewById<CircleImageView>(R.id.mailbox_nav_button)
        }

        private val newMailExtraAccounts: TextView by lazy {
            mailboxView.findViewById<TextView>(R.id.badge_new_mails)
        }

        private val openComposerButton: View by lazy {
            mailboxView.findViewById<View>(R.id.fab)
        }

        private val backButton: ImageView by lazy {
            mailboxView.findViewById<ImageView>(R.id.mailbox_back_button)
        }

        private val trashBanner: RelativeLayout by lazy {
            mailboxView.findViewById<RelativeLayout>(R.id.empty_trash_banner)
        }

        private val emptyTrashButton: TextView by lazy {
            mailboxView.findViewById<TextView>(R.id.empty_trash)
        }

        private val updateBanner: LinearLayout by lazy {
            mailboxView.findViewById<LinearLayout>(R.id.update_banner)
        }

        private val updateBannerXButton: ImageView by lazy {
            mailboxView.findViewById<ImageView>(R.id.x_button)
        }

        override val virtualListView = VirtualRecyclerView(recyclerView)


        override fun attachView(
                threadEventListener: EmailThreadAdapter.OnThreadEventListener,
                onDrawerMenuItemListener: DrawerMenuItemListener,
                observer: MailboxUIObserver,
                threadList: VirtualEmailThreadList, activeAccount: ActiveAccount) {

            drawerMenuView = DrawerMenuView(leftNavigationView, onDrawerMenuItemListener)

            adapter = EmailThreadAdapter(threadListener = threadEventListener,
                                        threadList = threadList,
                                        activeAccount = activeAccount)

            initMailboxAvatar(activeAccount.name, activeAccount.userEmail, activeAccount.type)

            refreshLayout.setProgressBackgroundColorSchemeColor(context.getColorFromAttr(R.attr.criptextColorBackground))
            refreshLayout.setColorSchemeColors(ContextCompat.getColor(context, R.color.colorAccent))

            virtualListView.setAdapter(adapter)
            this.observer = observer
            this.threadEventListener = threadEventListener
            setListeners(observer)
        }

        override fun initDrawerLayout() {
            navButton.setOnClickListener{
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        override fun initMailboxAvatar(fullName: String, email: String, accountType: AccountTypes) {
            val domain = EmailAddressUtils.extractEmailAddressDomain(email)
            UIUtils.setProfilePicture(
                    avatar = navButton,
                    avatarRing = toolbarHolder.getAvatarRingImage(),
                    resources = context.resources,
                    recipientId = EmailAddressUtils.extractRecipientIdFromAddress(email, domain),
                    name = fullName,
                    runnable = null,
                    domain = domain)
            toolbarHolder.showPlusBadge(AccountUtils.isPlus(accountType))
        }

        override fun showExtraAccountsBadge(show: Boolean) {
            newMailExtraAccounts.visibility = if(show) View.VISIBLE else View.GONE
        }

        override fun hideMultipleAccountsMenu() {
            drawerMenuView.hideMultipleAccountsMenu()
        }

        override fun showLinkDeviceAuthConfirmation(untrustedDeviceInfo: DeviceInfo.UntrustedDeviceInfo) {
            if(linkAuthDialog.isShowing() != null && linkAuthDialog.isShowing() == false)
                linkAuthDialog.showLinkDeviceAuthDialog(observer, untrustedDeviceInfo)
            else if(linkAuthDialog.isShowing() == null)
                linkAuthDialog.showLinkDeviceAuthDialog(observer, untrustedDeviceInfo)
        }

        override fun showSyncDeviceAuthConfirmation(trustedDeviceInfo: DeviceInfo.TrustedDeviceInfo) {
            if(syncAuthDialog.isShowing() != null && syncAuthDialog.isShowing() == false)
                syncAuthDialog.showLinkDeviceAuthDialog(observer, trustedDeviceInfo)
            else if(syncAuthDialog.isShowing() == null)
                syncAuthDialog.showLinkDeviceAuthDialog(observer, trustedDeviceInfo)
        }

        override fun showUpdateNowDialog() {
            val dialog = GeneralDialogConfirmation(
                    context = context,
                    data = DialogData.DialogConfirmationData(
                            type = DialogType.UpdateApp(),
                            title = UIMessage(R.string.password_warning_dialog_title),
                            message = listOf(UIMessage(R.string.version_not_supported_dialog_message)),
                            confirmButtonText = R.string.update_btn
                    )
            )
            dialog.showDialog(observer)
        }

        override fun dismissLinkDeviceDialog() {
            linkAuthDialog.dismiss()
        }

        override fun dismissSyncDeviceDialog() {
            syncAuthDialog.dismiss()
        }

        override fun onBackPressed(): Boolean {
            return when {
                drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                    drawerLayout.closeDrawer(Gravity.LEFT)
                    false
                }
                drawerLayout.isDrawerOpen(GravityCompat.END) -> {
                    drawerLayout.closeDrawer(Gravity.RIGHT)
                    false
                }
                else -> true
            }
        }

        override fun initNavHeader(fullName: String, email: String, accountType: AccountTypes) {
            drawerMenuView.initNavHeader(fullName, email, accountType)
        }

        override fun scrollTop() {
            recyclerView.smoothScrollToPosition(0)
        }

        override fun refreshToolbarItems() {
            hostActivity.refreshToolbarItems()
        }

        override fun showMultiModeBar(selectedThreadsQuantity : Int) {
            toolbarHolder.showMultiModeBar(selectedThreadsQuantity)
        }

        override fun hideMultiModeBar() {
            toolbarHolder.hideMultiModeBar()
        }

        override fun updateToolbarTitle(title: String) {
            toolbarHolder.updateToolbarTitle(title)
        }

        override fun showDialogLabelsChooser( labelDataSourceHandler:
                                              LabelDataHandler ) {
            labelChooserDialog.showDialogLabelsChooser(dataHandler = labelDataSourceHandler)
        }

        override fun openNotificationFeed(){
            drawerLayout.openDrawer(GravityCompat.END)
        }

        override fun showDialogMoveTo(onMoveThreadsListener: OnMoveThreadsListener,
                                      currentFolder: String) {
            this.onMoveThreadsListener = onMoveThreadsListener
            moveToDialog.showMoveToDialog(
                    onMoveThreadsListener = onMoveThreadsListener,
                    currentFolder = currentFolder)
        }

        override fun showDialogDeleteThread(onDeleteThreadListener: OnDeleteThreadListener) {
            deleteDialog.showDeleteThreadDialog(onDeleteThreadListener)
        }

        override fun showSyncPhonebookDialog(observer: MailboxUIObserver) {
            syncPhonebookDialog.showSyncPhonebookDialog(observer)
        }

        override fun showStartGuideEmail() {
            observer?.showStartGuideEmail(openComposerButton)
        }

        override fun showStartGuideMultiple(){
            if(recyclerView.adapter != null && recyclerView.adapter!!.itemCount > 2) {
                val view = recyclerView.findViewHolderForAdapterPosition(0) ?: return
                observer?.showStartGuideMultiple(view.itemView.mail_item_left_name)
            }
        }

        override fun showSecureLockGuide(position: Int) {
            if(recyclerView.adapter != null && recyclerView.adapter!!.itemCount > 2) {
                val view = recyclerView.findViewHolderForAdapterPosition(position) ?: return
                observer?.showSecureIconGuide(view.itemView.findViewById(R.id.email_is_secure))
            }
        }

        override fun showEmptyTrashWarningDialog(onEmptyTrashListener: OnEmptyTrashListener) {
            emptyTrashDialog.showEmptyTrashDialog(onEmptyTrashListener)
        }

        override fun showWelcomeDialog(observer: MailboxUIObserver) {
            welcomeDialog.showWelcomeTourDialog(observer)
        }

        override fun showRestoreBackupDialog(observer: MailboxUIObserver) {
            restoreBackupDialog.showDialog(observer)
        }

        override fun getGoogleDriveService(): Drive? {
            val googleAccount = GoogleSignIn.getLastSignedInAccount(context) ?: return null
            val credential = GoogleAccountCredential.usingOAuth2(
                    context, Collections.singleton(DriveScopes.DRIVE_FILE))
            credential.selectedAccount = googleAccount.account
            return Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory(),
                    credential)
                    .setApplicationName("Criptext Secure Email")
                    .build()
        }

        override fun checkRating(storage: KeyValueStorage) {
            AppRater.appLaunched(context, storage)
        }

        override fun showRecommendBackupDialog() {
            recommendBackupDialog.showDialog(observer)
        }

        override fun scheduleCloudBackupJob(period: Int, accountId: Long, useWifiOnly: Boolean) {
            val cloudBackupJobService = CloudBackupJobService()
            cloudBackupJobService.schedule(context, AccountUtils.getFrequencyPeriod(period), accountId, useWifiOnly)
        }

        override fun removeFromScheduleCloudBackupJob(accountId: Long) {
            val cloudBackupJobService = CloudBackupJobService()
            cloudBackupJobService.cancel(context, accountId)
        }

        override fun dismissAccountSuspendedDialog() {
            accountSuspended.dismissDialog()
        }

        override fun showAccountSuspendedDialog(observer: UIObserver, email: String, dialogType: DialogType) {
            accountSuspended.showDialog(observer, email, dialogType)
        }

        override fun setToolbarNumberOfEmails(emailsSize: Int) {
            toolbarHolder.updateNumberOfMails(emailsSize)
        }

        override fun hideEmptyTrashBanner() {
            trashBanner.visibility = View.GONE
        }

        override fun showEmptyTrashBanner() {
            trashBanner.visibility = View.VISIBLE
        }

        override fun showPreparingFileDialog() {
            preparingFileDialog.showDialog()
        }

        override fun dismissPreparingFileDialog() {
            preparingFileDialog.dismiss()
        }

        override fun showUpdateBanner(bannerData: UpdateBannerData) {
            updateBanner.findViewById<TextView>(R.id.banner_title).text = bannerData.title
            updateBanner.findViewById<TextView>(R.id.banner_message).text = bannerData.message
            Picasso.get().load(bannerData.image)
                    .into(updateBanner.findViewById<ImageView>(R.id.banner_image))
            UIUtils.expand(updateBanner)
        }

        override fun hideUpdateBanner() {
            UIUtils.collapse(updateBanner)
        }

        override fun setUpdateBannerClick(){
            val bannerMessage = updateBanner.findViewById<TextView>(R.id.banner_message)
            bannerMessage.isClickable = true
            bannerMessage.setOnClickListener {
                hostActivity.launchExternalActivityForResult(ExternalActivityParams.OpenGooglePlay())
                hideUpdateBanner()
            }
        }

        override fun clearUpdateBannerClick() {
            val bannerMessage = updateBanner.findViewById<TextView>(R.id.banner_message)
            bannerMessage.setOnClickListener { }
            bannerMessage.isClickable = false
        }

        override fun setEmtpyMailboxBackground(label: Label) {
            val image = mailboxView.findViewById<ImageView>(R.id.envelope_opened)
            val messageTop = mailboxView.findViewById<TextView>(R.id.no_results_label)
            val messageBottom = mailboxView.findViewById<TextView>(R.id.no_results_label_bottom)
            if(image != null){
                when(label){
                    Label.defaultItems.starred -> {
                        messageTop.text = context.getLocalizedUIMessage(UIMessage(R.string.empty_starred_message_top))
                        messageBottom.text = context.getLocalizedUIMessage(UIMessage(R.string.empty_starred_message_bottom))
                        if(AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)
                            image.setImageResource(R.drawable.starred_dark)
                        else
                            image.setImageResource(R.drawable.starred_light)
                    }
                    Label.defaultItems.sent -> {
                        messageTop.text = context.getLocalizedUIMessage(UIMessage(R.string.empty_sent_message_top))
                        messageBottom.text = context.getLocalizedUIMessage(UIMessage(R.string.empty_sent_message_bottom))
                        if(AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)
                            image.setImageResource(R.drawable.sent_dark)
                        else
                            image.setImageResource(R.drawable.sent_light)
                    }
                    Label.defaultItems.spam -> {
                        messageTop.text = context.getLocalizedUIMessage(UIMessage(R.string.empty_spam_message_top))
                        messageBottom.text = context.getLocalizedUIMessage(UIMessage(R.string.empty_spam_message_bottom))
                        if(AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)
                            image.setImageResource(R.drawable.spam_dark)
                        else
                            image.setImageResource(R.drawable.spam_light)
                    }
                    Label.defaultItems.trash -> {
                        messageTop.text = context.getLocalizedUIMessage(UIMessage(R.string.empty_trash_message_top))
                        messageBottom.text = context.getLocalizedUIMessage(UIMessage(R.string.empty_trash_message_bottom))
                        if(AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)
                            image.setImageResource(R.drawable.trash_dark)
                        else
                            image.setImageResource(R.drawable.trash_light)
                    }
                    Label.defaultItems.draft -> {
                        messageTop.text = context.getLocalizedUIMessage(UIMessage(R.string.empty_draft_message_top))
                        messageBottom.text = context.getLocalizedUIMessage(UIMessage(R.string.empty_draft_message_bottom))
                        if(AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)
                            image.setImageResource(R.drawable.draft_dark)
                        else
                            image.setImageResource(R.drawable.draft_light)
                    }
                    else -> {
                        if(label.text == Label.LABEL_ALL_MAIL){
                            messageTop.text = context.getLocalizedUIMessage(UIMessage(R.string.empty_allmail_message_top))
                            messageBottom.text = context.getLocalizedUIMessage(UIMessage(R.string.empty_allmail_message_bottom))
                        }else{
                            messageTop.text = context.getLocalizedUIMessage(UIMessage(R.string.empty_inbox_message_top))
                            messageBottom.text = context.getLocalizedUIMessage(UIMessage(R.string.empty_inbox_message_bottom))
                        }
                        if(AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)
                            image.setImageResource(R.drawable.inbox_dark)
                        else
                            image.setImageResource(R.drawable.inbox_light)
                    }
                }
            }
        }

        override fun onFetchedSelectedLabels(defaultSelectedLabels: List<Label>, labels: List<Label>) {
            labelChooserDialog.onFetchedLabels(
                    defaultSelectedLabels = defaultSelectedLabels,
                    allLabels = labels)
        }

        override fun refreshMails() {
            if(refreshLayout.isRefreshing) {
                Handler().postDelayed({
                    refreshLayout.isRefreshing = false
                }, 1000)
            }
        }

        override fun showRefresh() {
            refreshLayout.isRefreshing = true
        }

        override fun clearRefreshing() {
            refreshLayout.isRefreshing = false
        }

        override fun showMessage(message: UIMessage, showAction: Boolean) {
            SnackBarHelper.show(
                    openComposerButton,
                    context.getLocalizedUIMessage(message), observer as UIObserver,
                    showAction
                    )
        }

        override fun showToastMessage(message: UIMessage) {
            val duration = Toast.LENGTH_LONG
            val toast = Toast.makeText(
                    context,
                    context.getLocalizedUIMessage(message),
                    duration)
            toast.show()
        }

        override fun hideDrawer() {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        override fun setCounterLabel(menu: NavigationMenuOptions, total: Int) {
            drawerMenuView.setCounterLabel(menu, total)
        }

        override fun updateBadges(badgeData: List<Pair<String, Int>>) {
            drawerMenuView.updateBadges(badgeData)
        }

        private fun setListeners(observer: MailboxUIObserver){

            openComposerButton.setOnClickListener {
                observer.onOpenComposerButtonClicked()
            }

            refreshLayout.setOnRefreshListener {
                observer.onRefreshMails()
            }

            backButton.setOnClickListener {
                observer.onBackButtonPressed()
            }

            emptyTrashButton.setOnClickListener{
                observer.onEmptyTrashPressed()
            }

            updateBannerXButton.setOnClickListener {
                observer.onUpdateBannerXPressed()
            }


            drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener{
                override fun onDrawerStateChanged(newState: Int) {}
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
                override fun onDrawerClosed(drawerView: View) {
                    if(drawerView == rightNavigationView){
                        observer.onFeedDrawerClosed()
                    }
                }
                override fun onDrawerOpened(drawerView: View) {}
            })
        }

        override fun setMenuLabels(labels: List<LabelWrapper>) {
            drawerMenuView.setLabelAdapter(labels)
        }

        override fun setMenuAccounts(accounts: List<Account>, badgeCount: List<Int>) {
            drawerMenuView.setAccountAdapter(accounts, badgeCount)
        }

        override fun clearMenuActiveLabel() {
            drawerMenuView.clearActiveLabel()
        }

        override fun hideComposer(shouldHide: Boolean) {
            openComposerButton.visibility = if(shouldHide) View.GONE else View.VISIBLE
        }

        override fun showNotification() {
            val vibrate: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrate.vibrate(VibrationEffect.createOneShot(400,10))
            } else {
                @Suppress("DEPRECATION")
                vibrate.vibrate(400)
            }

        }
    }
}
