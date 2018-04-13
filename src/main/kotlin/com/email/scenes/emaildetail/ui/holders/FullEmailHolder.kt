package com.email.scenes.emaildetail.ui.holders

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.widget.AppCompatImageView
import android.support.v7.widget.PopupMenu
import android.util.DisplayMetrics
import android.view.*
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import com.email.R
import com.email.db.DeliveryTypes
import com.email.db.models.FullEmail
import com.email.scenes.emaildetail.WebviewJavascriptInterface
import com.email.scenes.emaildetail.ui.AttachmentHistoryPopUp
import com.email.scenes.emaildetail.ui.EmailContactInfoPopup
import com.email.scenes.emaildetail.ui.FullEmailListAdapter
import com.email.scenes.emaildetail.ui.ReadHistoryPopUp
import com.email.utils.HTMLUtils
import com.email.utils.ui.ZoomLayout
import com.email.utils.WebViewUtils
import com.github.ybq.android.spinkit.SpinKitView

/**
 * Created by sebas on 3/12/18.
 */

class FullEmailHolder(view: View) : ParentEmailHolder(view) {

    private val context = view.context
    private val layout : FrameLayout
    private val replyView: ImageView
    private val moreView: ImageView
    private val toView: TextView
    private val attachmentView: ImageView
    private val readView: ImageView
    private val unsendView: AppCompatImageView
    private val layoutAttachment : RelativeLayout
    private val contactInfoPopUp: EmailContactInfoPopup
    private val readHistoryPopUp: ReadHistoryPopUp
    private val attachmentHistoryPopUp: AttachmentHistoryPopUp
    private val bodyWebView: WebView
    private val zoomLayout: ZoomLayout
    private val horizontalScrollView: HorizontalScrollView
    private val progressBarUnsend: SpinKitView
    private val bodyContainer : LinearLayout
    private val webViewLoader: ProgressBar

    override fun setListeners(fullEmail: FullEmail,
                     emailListener: FullEmailListAdapter.OnFullEmailEventListener?,
                     adapter: FullEmailListAdapter, position: Int) {
        view.setOnClickListener {

            emailListener?.ontoggleViewOpen(
                    fullEmail = fullEmail,
                    position = position,
                    viewOpen = false)
        }
        moreView.setOnClickListener({
            displayPopMenu(emailListener, fullEmail, adapter, position)
        })

        readView.setOnClickListener({
            readHistoryPopUp.createPopup(fullEmail, null)
        })

        toView.setOnClickListener({
            contactInfoPopUp.createPopup(fullEmail, null)
        })

        attachmentView.setOnClickListener {
            attachmentHistoryPopUp.createPopup(fullEmail, null)
        }

        layoutAttachment.setOnClickListener{
            TODO("HANDLE CLICK TO ATTACHMENT")
        }

        replyView.setOnClickListener{
            emailListener?.onReplyOptionSelected(
                    fullEmail = fullEmail,
                    position = position,
                    all = false)
        }

        unsendView.setOnClickListener {
            toggleUnsendProgress(isShown = true)
            deactivateElementsWhileUnsending()
            emailListener?.onUnsendEmail(
                    fullEmail = fullEmail,
                    position = position)
        }
    }
    private fun toggleUnsendProgress(isShown: Boolean) {
        if(isShown)  {
            progressBarUnsend.visibility = View.VISIBLE
            unsendView.visibility = View.GONE
        } else {
            progressBarUnsend.visibility = View.GONE
            unsendView.visibility = View.VISIBLE
        }
    }
    private fun displayPopMenu(emailListener: FullEmailListAdapter.OnFullEmailEventListener?, fullEmail: FullEmail,
                               adapter: FullEmailListAdapter, position: Int){
        val popupMenu = createPopupMenu(fullEmail)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.reply_all ->
                    emailListener?.onReplyAllOptionSelected(
                            fullEmail = fullEmail,
                            position = position,
                            all = false)
                R.id.reply ->
                    emailListener?.onReplyOptionSelected(
                            fullEmail = fullEmail,
                            position = position,
                            all = false)
                R.id.forward ->
                    emailListener?.onForwardOptionSelected(
                            fullEmail = fullEmail,
                            position = position,
                            all = false)
                R.id.mark_read, R.id.mark_unread -> {
                    emailListener?.onToggleReadOption(
                            fullEmail = fullEmail,
                            position = position,
                            markAsRead = item.itemId == R.id.mark_read)
                }
                R.id.delete ->
                    emailListener?.onDeleteOptionSelected(
                            fullEmail = fullEmail,
                            position = position )
            }
            false
        }

        popupMenu.gravity = Gravity.END
        popupMenu.show()

    }

    private fun createPopupMenu(fullEmail: FullEmail): PopupMenu {
        val wrapper = ContextThemeWrapper(context, R.style.email_detail_popup_menu)
        val popupMenu = PopupMenu(wrapper , moreView)

    val popuplayout =
            if (fullEmail.email.unread)
                R.menu.mail_options_unread_menu
            else
                R.menu.mail_options_read_menu

        popupMenu.inflate(popuplayout)
        return popupMenu
    }

    @SuppressLint("RestrictedApi")
    override fun bindFullMail(fullEmail: FullEmail) {
        toggleUnsendProgress(isShown = false)
        setDefaultBackgroundColors()
        if(fullEmail.email.delivered != DeliveryTypes.UNSENT) {
            bodyWebView.loadDataWithBaseURL("", HTMLUtils.
                    changedHeaderHtml(fullEmail.email.content), "text/html", "utf-8", "")
        } else {
            val attachmentImage = layoutAttachment.findViewById<ImageView>(R.id.attachment_container)
            attachmentImage.setImageDrawable(
                    ContextCompat.getDrawable(view.context, R.drawable.eliminar_attachment_lock))
            bodyWebView.loadDataWithBaseURL("", HTMLUtils.
                    changedHeaderHtml("This content was unsent"), "text/html", "utf-8", "")

            val unsendDrawable = DrawableCompat.wrap(unsendView.drawable)

            unsendView.supportBackgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(view.context, R.color.white))

            DrawableCompat.setTintList(unsendDrawable, ColorStateList.valueOf(ContextCompat.getColor(view.context, R.color.unsend_button_red)))
        }

        val numberContacts = fullEmail.to.size
        if(fullEmail.from != null) {
            view.findViewById<LinearLayout>(R.id.container_my_email_options).visibility = View.INVISIBLE
            headerView.text = fullEmail.from.name
            toView.text = if(numberContacts > 0)
                "To me and ${numberContacts  - 1} contacts" else "To me and $numberContacts contacts"
        }
        else {
            view.findViewById<LinearLayout>(R.id.container_my_email_options).visibility = View.VISIBLE
            headerView.text = "Me"
            replyView.visibility = View.GONE
            toView.text = "To $numberContacts contacts"
        }
    }

    fun setupWebview(){
        val metrics = DisplayMetrics()
        val display = (context.getSystemService(
                Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        display.getMetrics(metrics)
        bodyWebView.layoutParams = FrameLayout.LayoutParams(
                metrics.widthPixels - context.resources.
                        getDimension(R.dimen.webview_left_margin).toInt(), bodyWebView.layoutParams.height)

        val webSettings = bodyWebView.settings
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webSettings.builtInZoomControls = false
        webSettings.displayZoomControls = false
        webSettings.javaScriptEnabled = true
        webSettings.allowFileAccess = true
        bodyWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                WebViewUtils.openUrl(bodyWebView.context!!, url)
                zoomLayout.visibility = View.GONE
                webViewLoader.visibility = View.VISIBLE
                return true
            }
            @TargetApi(Build.VERSION_CODES.N)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                WebViewUtils.openUrl(bodyWebView.context!!, request.url.toString())
                zoomLayout.visibility = View.GONE
                webViewLoader.visibility = View.VISIBLE
                return true
            }


            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                zoomLayout.visibility = View.VISIBLE
                webViewLoader.visibility = View.GONE
                view?.evaluateJavascript("""window.scrollTo(0,0);""") { }

                val treeObserver = horizontalScrollView.viewTreeObserver

                treeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                         horizontalScrollView.viewTreeObserver.removeGlobalOnLayoutListener(this)
                         horizontalScrollView.scrollTo(0, 0)
                    }
                })
            }
        }
        val javascriptInterface = WebviewJavascriptInterface(
                context, zoomLayout)
        bodyWebView.addJavascriptInterface(javascriptInterface, "CriptextSecureEmail")
    }

    private fun deactivateElementsWhileUnsending() {
        bodyContainer.alpha = 0.5.toFloat()
        bodyContainer.isEnabled = false
    }

    private fun setDefaultBackgroundColors() {
        bodyContainer.alpha = 1.toFloat()
        bodyContainer.isEnabled = true
    }
    init {
        layout = view.findViewById(R.id.open_full_mail_item_container)
        toView = view.findViewById(R.id.to)
        moreView = view.findViewById(R.id.more)
        replyView = view.findViewById(R.id.reply)
        attachmentView =  view.findViewById(R.id.attachment)
        readView =  view.findViewById(R.id.read)
        unsendView =  view.findViewById(R.id.unsend)

        layoutAttachment = view.findViewById(R.id.open_full_mail_attachment_container)
        contactInfoPopUp = EmailContactInfoPopup(toView)
        readHistoryPopUp = ReadHistoryPopUp(readView)
        attachmentHistoryPopUp = AttachmentHistoryPopUp(attachmentView)
        bodyWebView = view.findViewById(R.id.email_body)

        bodyWebView.webChromeClient = WebChromeClient()
        zoomLayout = view.findViewById(R.id.full_mail_zoom)
        horizontalScrollView = view.findViewById(R.id.full_mail_scroll)
        progressBarUnsend = view.findViewById(R.id.spin_kit_unsend)
        bodyContainer = view.findViewById(R.id.body_container)
        webViewLoader = view.findViewById(R.id.progress_bar_webview_loading)

        setupWebview()
        horizontalScrollView.isHorizontalScrollBarEnabled = false
        zoomLayout.slideContainer = { dx: Int ->
            horizontalScrollView.smoothScrollBy(dx - horizontalScrollView.scrollX, 0)
        }
    }

}