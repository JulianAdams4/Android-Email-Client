package com.email.scenes.signin.holders

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.res.ColorStateList
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.design.widget.TextInputLayout
import android.support.v4.content.ContextCompat
import android.support.v7.widget.AppCompatEditText
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewTreeObserver
import android.view.animation.AccelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.email.R
import com.email.utils.UIMessage
import com.email.utils.compat.ViewAnimationUtilsCompat
import com.email.utils.getLocalizedUIMessage
import com.email.validation.ProgressButtonState

/**
 * Created by sebas on 3/2/18.
 */

class SignInStartHolder(
        val view: View,
        initialUsername: String,
        firstTime: Boolean): BaseSignInHolder() {

    private val rootLayout: View = view.findViewById<View>(R.id.viewRoot)
    private val usernameInput : AppCompatEditText = view.findViewById(R.id.input_username)
    private val usernameInputLayout : TextInputLayout = view.findViewById(R.id.input_username_layout)
    private val signInButton : Button = view.findViewById(R.id.signin_button)
    private val signUpTextView: TextView = view.findViewById(R.id.signup_textview)
    private val progressBar: ProgressBar = view.findViewById(R.id.signin_progress_login)
    private val imageError: ImageView = view.findViewById(R.id.signin_error_image)

    init {
        usernameInput.text = SpannableStringBuilder(initialUsername)
        showNormalTints()
        setListeners()

        if(firstTime) {
            rootLayout.visibility = View.INVISIBLE
            addGlobalLayout()
        }
    }

    private fun addGlobalLayout(){

        val viewTreeObserver = rootLayout.viewTreeObserver
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    revealActivity(view.resources.displayMetrics.widthPixels / 2,
                            view.resources.displayMetrics.heightPixels / 2)
                    rootLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            })
        }
    }

    private fun revealActivity(x: Int, y: Int) {

        val finalRadius = (Math.max(rootLayout.width, rootLayout.height) * 1.1).toFloat()
        val circularReveal = ViewAnimationUtilsCompat.createCircularReveal(rootLayout, x, y, 0f, finalRadius)
        rootLayout.visibility = View.VISIBLE;
        circularReveal?.start()

    }

    private fun showNormalTints(){
        usernameInputLayout.setHintTextAppearance(R.style.textinputlayout_login)
        setUsernameBackgroundTintList()
    }

    @SuppressLint("RestrictedApi")
    private fun setUsernameBackgroundTintList() {
        usernameInput.supportBackgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(view.context, R.color.signup_hint_color))
    }

    private fun setListeners() {

        signInButton.setOnClickListener {
            uiObserver?.onSubmitButtonClicked()
        }
        signUpTextView.setOnClickListener{
            uiObserver?.onSignUpLabelClicked()
        }
        usernameInputLayout.setOnFocusChangeListener { _, isFocused ->
            uiObserver?.toggleUsernameFocusState(isFocused)
        }

        usernameInput.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(text: CharSequence?, p1: Int, p2: Int, p3: Int) {
                uiObserver?.onUsernameTextChanged(text.toString())
            }
        })
    }

    @SuppressLint("RestrictedApi")
    fun drawError(uiMessage: UIMessage) {
        usernameInputLayout.setHintTextAppearance(R.style.textinputlayout_login_error)
        usernameInput.supportBackgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(view.context, R.color.black))
        imageError.visibility = View.VISIBLE
        usernameInput.error = view.context.getLocalizedUIMessage(uiMessage)
        signInButton.isEnabled  = false
    }

    fun setSubmitButtonState(state : ProgressButtonState) {
        when (state) {
            ProgressButtonState.disabled -> {
                signInButton.visibility = View.VISIBLE
                signInButton.isEnabled = false
                progressBar.visibility = View.GONE
            }
            ProgressButtonState.enabled -> {
                signInButton.visibility = View.VISIBLE
                signInButton.isEnabled = true
                progressBar.visibility = View.GONE
            }
            ProgressButtonState.waiting -> {
                signInButton.visibility = View.GONE
                signInButton.isEnabled = false
                progressBar.visibility = View.VISIBLE
            }
        }
    }

}