package com.criptext.mail.scenes.signin

import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.criptext.mail.R
import com.criptext.mail.scenes.signin.holders.*
import com.criptext.mail.scenes.signup.holders.KeyGenerationHolder
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.utils.getLocalizedUIMessage
import com.criptext.mail.validation.ProgressButtonState

/**
 * Created by sebas on 2/15/18.
 */

interface SignInScene {
    fun resetInput()
    fun showError(message: UIMessage)
    fun drawInputError(error: UIMessage)
    fun drawSuccess()
    fun initLayout(state: SignInLayoutState, signInUIObserver: SignInSceneController.SignInUIObserver)
    fun showPasswordLoginDialog(
            onPasswordLoginDialogListener: OnPasswordLoginDialogListener)
    fun setSubmitButtonState(state: ProgressButtonState)
    fun showKeyGenerationHolder()

    var signInUIObserver: SignInSceneController.SignInUIObserver?

    class SignInSceneView(val view: View): SignInScene {

        private val viewGroup = view.parent as ViewGroup
        private var holder: BaseSignInHolder

        override var signInUIObserver: SignInSceneController.SignInUIObserver? = null
            set(value) {
                holder.uiObserver = value
                field = value
            }

        override fun showError(message: UIMessage) {
            val duration = Toast.LENGTH_LONG
            val toast = Toast.makeText(
                    view.context,
                    view.context.getLocalizedUIMessage(message),
                    duration)
            toast.show()
        }

        override fun drawSuccess() {
        }

        init {
            val signInLayout = View.inflate(
                    view.context,
                    R.layout.activity_signin_form, viewGroup)
            holder = SignInStartHolder(signInLayout, "", true)
        }


        override fun initLayout(state: SignInLayoutState, signInUIObserver: SignInSceneController.SignInUIObserver) {
            removeAllViews()
            holder = when (state) {
                is SignInLayoutState.WaitForApproval -> {
                    val newLayout = View.inflate(
                            view.context,
                            R.layout.activity_connection, viewGroup)
                    ConnectionHolder(newLayout)
                }

                is SignInLayoutState.InputPassword -> {
                    val newLayout = View.inflate(
                            view.context,
                            R.layout.activity_password_login, viewGroup)
                    PasswordLoginHolder(newLayout, state)
                }

                is SignInLayoutState.LoginValidation -> {
                    val newLayout = View.inflate(
                            view.context,
                            R.layout.activity_login_validation, viewGroup)
                    LoginValidationHolder(newLayout, state)
                }

                is SignInLayoutState.Start -> {
                    val newLayout = View.inflate(
                            view.context,
                            R.layout.activity_signin_form, viewGroup)
                    SignInStartHolder(newLayout, state.username, state.firstTime)
                }
            }
            holder.uiObserver = signInUIObserver
        }


        private fun removeAllViews() {
            viewGroup.removeAllViews()
            holder.uiObserver = null
        }

        private val showMailboxScene = {
            signInUIObserver: SignInSceneController.SignInUIObserver ->
            signInUIObserver.userLoginReady()
        }

        override fun resetInput() {
            val currentHolder = holder
            when (currentHolder) {
                is PasswordLoginHolder -> currentHolder.resetInput()
            }
        }

        override fun setSubmitButtonState(state: ProgressButtonState) {
            val currentHolder = holder
            when (currentHolder) {
                is PasswordLoginHolder -> currentHolder.setSubmitButtonState(state)
                is SignInStartHolder -> currentHolder.setSubmitButtonState(state)
            }
        }

        override fun showPasswordLoginDialog(onPasswordLoginDialogListener: OnPasswordLoginDialogListener) {
            (holder as LoginValidationHolder).showPasswordLoginDialog(onPasswordLoginDialogListener)
        }

        override fun drawInputError(error: UIMessage) {
            val currentHolder = holder
            when (currentHolder) {
                is SignInStartHolder -> currentHolder.drawError(error)
            }
        }

        override fun showKeyGenerationHolder() {
            viewGroup.removeAllViews()
            val keyGenerationLayout = View.inflate(
                    view.context,
                    R.layout.view_key_generation, viewGroup)
            KeyGenerationHolder(keyGenerationLayout, {
                if(it >= 100){
                    holder.uiObserver?.onProgressHolderFinish()
                }
            }, 50)
        }

    }
}
