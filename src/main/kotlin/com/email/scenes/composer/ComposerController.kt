package com.email.scenes.composer

import android.Manifest
import android.content.DialogInterface
import com.email.ExternalActivityParams
import com.email.IHostActivity
import com.email.R
import com.email.bgworker.BackgroundWorkManager
import com.email.db.models.ActiveAccount
import com.email.scenes.ActivityMessage
import com.email.scenes.SceneController
import com.email.scenes.composer.data.*
import com.email.scenes.composer.ui.ComposerUIObserver
import com.email.scenes.params.MailboxParams
import com.email.utils.UIMessage
import android.content.pm.PackageManager
import com.email.BaseActivity
import com.email.scenes.params.EmailDetailParams


/**
 * Created by gabriel on 2/26/18.
 */

class ComposerController(private val model: ComposerModel,
                         private val scene: ComposerScene,
                         private val host: IHostActivity,
                         private val activeAccount: ActiveAccount,
                         private val dataSource: BackgroundWorkManager<ComposerRequest, ComposerResult>)
    : SceneController() {

    private val dataSourceController = DataSourceController(dataSource)

    private val observer = object: ComposerUIObserver {
        override fun onAttachmentRemoveClicked(position: Int) {
            model.attachments.removeAt(position)
            scene.notifyAttachmentSetChanged()
        }

        override fun onSelectedEditTextChanged(userIsEditingRecipients: Boolean) {
            scene.toggleExtraFieldsVisibility(visible = userIsEditingRecipients)
        }

        override fun onRecipientListChanged() {
            val data = scene.getDataInputByUser()
            updateModelWithInputData(data)
            host.refreshToolbarItems()
        }

        override fun onAttachmentButtonClicked() {
            if(host.checkPermissions(BaseActivity.RequestCode.writeAccess.ordinal,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                host.launchExternalActivityForResult(ExternalActivityParams.FilePicker())
            }
        }

        override fun onBackButtonClicked() {
            if(shouldGoBackWithoutSave()){
                exitToEmailDetailScene()
            }
            else{
                showDraftDialog()
            }
        }
    }

    private val dataSourceListener: (ComposerResult) -> Unit = { result ->
        when(result) {
            is ComposerResult.GetAllContacts -> onContactsLoaded(result)
            is ComposerResult.SaveEmail -> onEmailSavesAsDraft(result)
            is ComposerResult.DeleteDraft -> exitToEmailDetailScene()
            is ComposerResult.UploadFile -> onUploadFile(result)
            is ComposerResult.LoadInitialData -> onLoadedInitialData(result)
        }
    }

    private fun onLoadedInitialData(result: ComposerResult.LoadInitialData) {
        when (result) {
            is ComposerResult.LoadInitialData.Success -> {
                updateModelWithInputData(result.initialData)
                bindWithModel(result.initialData, activeAccount.signature)
                model.initialized = true
            }

            is ComposerResult.LoadInitialData.Failure -> {
                scene.showError(result.message)
            }
        }
    }

    private fun onUploadFile(result: ComposerResult.UploadFile){
        when (result) {
            is ComposerResult.UploadFile.Register -> {
                val composerAttachment = getAttachmentByPath(result.filepath) ?: return
                composerAttachment.filetoken = result.filetoken
            }
            is ComposerResult.UploadFile.Progress -> {
                val composerAttachment = getAttachmentByPath(result.filepath) ?: return
                composerAttachment.uploadProgress = result.percentage
            }
            is ComposerResult.UploadFile.Success -> {
                val composerAttachment = getAttachmentByPath(result.filepath)
                composerAttachment?.uploadProgress = 100
                handleNextUpload()
            }
            is ComposerResult.UploadFile.Failure -> {
                removeAttachmentByPath(result.filepath)
                scene.showAttachmentErrorDialog(result.filepath)
                handleNextUpload()
            }
        }
        scene.notifyAttachmentSetChanged()
    }

    private fun getAttachmentByPath(filepath: String): ComposerAttachment? {
        return model.attachments.firstOrNull{it.filepath == filepath}
    }

    private fun removeAttachmentByPath(filepath: String) {
        model.attachments.removeAll{it.filepath == filepath}
    }

    private fun onEmailSavesAsDraft(result: ComposerResult.SaveEmail) {
        when (result) {
            is ComposerResult.SaveEmail.Success -> {
                if(result.onlySave) {
                    exitToEmailDetailScene()
                }
                else {
                    val sendMailMessage = ActivityMessage.SendMail(emailId = result.emailId,
                            threadId = result.threadId,
                            composerInputData = result.composerInputData,
                            attachments = result.attachments)
                    host.exitToScene(MailboxParams(), sendMailMessage, false)
                }
            }
            is ComposerResult.SaveEmail.Failure -> {
                scene.showError(UIMessage(R.string.error_saving_as_draft))
            }
        }
    }

    private fun onContactsLoaded(result: ComposerResult.GetAllContacts){
        when (result) {
            is ComposerResult.GetAllContacts.Success -> {
                scene.setContactSuggestionList(result.contacts.toTypedArray())
            }
            is ComposerResult.GetAllContacts.Failure -> {
                scene.showError(UIMessage(R.string.error_getting_contacts))
            }
        }
    }

    private fun updateModelWithInputData(data: ComposerInputData) {
        model.to.clear()
        model.to.addAll(data.to)
        model.cc.clear()
        model.cc.addAll(data.cc)
        model.bcc.clear()
        model.bcc.addAll(data.bcc)
        model.body = data.body
        model.subject = data.subject
    }

    private fun isReadyForSending() = model.to.isNotEmpty()

    private fun uploadSelectedFile(filepath: String){
        dataSource.submitRequest(ComposerRequest.UploadAttachment(filepath = filepath))
    }

    private fun saveEmailAsDraft(composerInputData: ComposerInputData, onlySave: Boolean) {
        val draftId = when (model.type) {
            is ComposerType.Draft -> model.type.draftId
            else -> null
        }
        val threadPreview =  when (model.type) {
            is ComposerType.Reply -> model.type.threadPreview
            is ComposerType.ReplyAll -> model.type.threadPreview
            is ComposerType.Forward -> model.type.threadPreview
            is ComposerType.Draft -> model.type.threadPreview
            else -> null
        }
        dataSource.submitRequest(ComposerRequest.SaveEmailAsDraft(
                threadId = threadPreview?.threadId,
                emailId = draftId,
                composerInputData = composerInputData,
                onlySave = onlySave, attachments = model.attachments))

    }

    private fun onSendButtonClicked() {
        val data = scene.getDataInputByUser()
        updateModelWithInputData(data)

        if(isReadyForSending()) {
            val validationError = Validator.validateContacts(data)
            if (validationError != null)
                scene.showError(validationError.toUIMessage())
            else
                saveEmailAsDraft(data, onlySave = false)



        } else
            scene.showError(UIMessage(R.string.no_recipients_error))
    }

    override val menuResourceId
        get() = if (isReadyForSending()) R.menu.composer_menu_enabled
                              else R.menu.composer_menu_disabled

    private fun addNewAttachments(filesMetadata: List<Pair<String, Long>>) {
        val isNewAttachment: (Pair<String, Long>) -> (Boolean) = { data ->
            model.attachments.indexOfFirst { it.filepath == data.first  } < 0
        }
        model.attachments.addAll(filesMetadata.filter(isNewAttachment).map {
            ComposerAttachment(it.first, it.second)
        })
        scene.notifyAttachmentSetChanged()
        handleNextUpload()
    }

    private fun handleNextUpload(){
        if(model.attachments.indexOfFirst { it.uploadProgress in 0..99 } >= 0){
            return
        }
        val attachmentToUpload = model.attachments.firstOrNull { it.uploadProgress == -1 } ?: return
        uploadSelectedFile(attachmentToUpload.filepath)
    }

    private fun handleActivityMessage(activityMessage: ActivityMessage?): Boolean {
        if (activityMessage is ActivityMessage.AddAttachments) {
            addNewAttachments(activityMessage.filesMetadata)
            return true
        }
        return false
    }

    private fun loadInitialData() {
        val type = model.type
        val request = when (type) {
            is ComposerType.Reply -> ComposerRequest.LoadInitialData(type, type.originalId)
            is ComposerType.ReplyAll -> ComposerRequest.LoadInitialData(type, type.originalId)
            is ComposerType.Forward -> ComposerRequest.LoadInitialData(type, type.originalId)
            is ComposerType.Draft -> ComposerRequest.LoadInitialData(type, type.draftId)
            else -> null
        }

        if (request != null) dataSource.submitRequest(request)
    }

    private fun bindWithModel(composerInputData: ComposerInputData, signature: String) {
        if(model.isReplyOrDraft){
            scene.setFocusToComposer()
        }
        scene.bindWithModel(firstTime = model.firstTime,
                composerInputData = composerInputData,
                attachments = model.attachments,
                signature = signature)
        model.firstTime = false
    }

    override fun onStart(activityMessage: ActivityMessage?): Boolean {

        dataSourceController.setDataSourceListener()

        if (model.initialized)
            bindWithModel(ComposerInputData.fromModel(model), activeAccount.signature)
        else
            loadInitialData()

        dataSourceController.getAllContacts()
        scene.observer = observer

        return handleActivityMessage(activityMessage)
    }

    override fun onStop() {
        // save state
        val data = scene.getDataInputByUser()
        updateModelWithInputData(data)

        scene.observer = null
        dataSource.listener = null
    }

    override fun onBackPressed(): Boolean {

        if(shouldGoBackWithoutSave()) {
            exitToEmailDetailScene()
        }
        else {
            showDraftDialog()

        }

        return false
    }

    private fun exitToEmailDetailScene(){
        val threadPreview =  when (model.type) {
            is ComposerType.Reply -> model.type.threadPreview
            is ComposerType.ReplyAll -> model.type.threadPreview
            is ComposerType.Forward -> model.type.threadPreview
            is ComposerType.Draft -> model.type.threadPreview
            else -> null
        }
        val currentLabel = when (model.type) {
            is ComposerType.Reply -> model.type.currentLabel
            is ComposerType.ReplyAll -> model.type.currentLabel
            is ComposerType.Forward -> model.type.currentLabel
            is ComposerType.Draft -> model.type.currentLabel
            else -> null
        }
        if(model.type is ComposerType.Empty || threadPreview == null || currentLabel == null){
            return host.finishScene()
        }
        val params = EmailDetailParams(threadId = threadPreview.threadId,
                currentLabel = currentLabel, threadPreview = threadPreview)
        host.exitToScene(params, null, true)
    }

    private fun shouldGoBackWithoutSave(): Boolean{
        val data = scene.getDataInputByUser()
        updateModelWithInputData(data)
        return !Validator.mailHasMoreThanSignature(data, activeAccount.signature)
    }

    private fun exitDeletingDraft() {
        val draftType = model.type as? ComposerType.Draft
        if (draftType != null)
            dataSource.submitRequest(ComposerRequest.DeleteDraft(draftType.draftId))
        else
            exitToEmailDetailScene()
    }

    private fun showDraftDialog(){
        val dialogClickListener = DialogInterface.OnClickListener { _, which ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE ->
                    saveEmailAsDraft(composerInputData = scene.getDataInputByUser(), onlySave = true)

                DialogInterface.BUTTON_NEGATIVE ->
                    exitDeletingDraft()
            }
        }

        scene.showDraftDialog(dialogClickListener)
    }

    override fun onOptionsItemSelected(itemId: Int) {
        when (itemId) {
            R.id.composer_send -> onSendButtonClicked()
        }
    }

    private inner class DataSourceController(
            private val dataSource: BackgroundWorkManager<ComposerRequest, ComposerResult>){

        fun setDataSourceListener() {
            dataSource.listener = dataSourceListener
        }

        fun getAllContacts(){
            val req = ComposerRequest.GetAllContacts()
            dataSource.submitRequest(req)
        }

    }

    override fun requestPermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != BaseActivity.RequestCode.writeAccess.ordinal) return

        val indexOfPermission = permissions.indexOfFirst { it == Manifest.permission.WRITE_EXTERNAL_STORAGE }
        if (indexOfPermission < 0) return
        if (grantResults[indexOfPermission] != PackageManager.PERMISSION_GRANTED){
            scene.showError(UIMessage(R.string.permission_filepicker_rationale))
            return
        }
        host.launchExternalActivityForResult(ExternalActivityParams.FilePicker())
    }
}