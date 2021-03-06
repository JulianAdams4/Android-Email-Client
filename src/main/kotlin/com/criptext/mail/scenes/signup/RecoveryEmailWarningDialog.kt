package com.criptext.mail.scenes.signup

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.criptext.mail.R

/**
 * Created by sebas on 2/8/18.
 */
class RecoveryEmailWarningDialog(val context: Context) {
    private var recoveryDialog: AlertDialog? = null
    private val res = context.resources

    fun showRecoveryEmailWarningDialog(onRecoveryEmailWarningListener : OnRecoveryEmailWarningListener) {
        val dialogBuilder = AlertDialog.Builder(context)
        val inflater = (context as AppCompatActivity).layoutInflater
        val dialogView = inflater.inflate(R.layout.recovery_email_warning_dialog, null)

        dialogBuilder.setView(dialogView)

        recoveryDialog = createDialog(dialogView,
                dialogBuilder,
                onRecoveryEmailWarningListener)
    }

    private fun createDialog(dialogView: View,
                             dialogBuilder: AlertDialog.Builder,
                             onRecoveryEmailWarningListener: OnRecoveryEmailWarningListener
    ): AlertDialog {
        val width = res.getDimension(R.dimen.recovery_email_warning_width).toInt()
        val newRecoveryEmailWarningDialog = dialogBuilder.create()
        newRecoveryEmailWarningDialog.show()

        val window = newRecoveryEmailWarningDialog.window
        window?.setLayout(width, LinearLayout.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.CENTER_VERTICAL)

        val drawableBackground = ContextCompat.getDrawable(
                dialogView.context, R.drawable.dialog_label_chooser_shape)
        newRecoveryEmailWarningDialog.window?.setBackgroundDrawable(drawableBackground)

        assignButtonEvents(dialogView,
                newRecoveryEmailWarningDialog,
                onRecoveryEmailWarningListener)

        return newRecoveryEmailWarningDialog
    }

    fun assignButtonEvents(view: View,
                           dialog: AlertDialog,
                           onRecoverEmailWarningListener: OnRecoveryEmailWarningListener) {

        val btn_yes = view.findViewById(R.id.recovery_email_warning_yes) as Button
        val btn_no = view.findViewById(R.id.recovery_email_warning_no) as Button

        btn_yes.setOnClickListener {
            onRecoverEmailWarningListener.denyWillAssignRecoverEmail()
            dialog.dismiss()
        }

        btn_no.setOnClickListener {
            onRecoverEmailWarningListener.willAssignRecoverEmail()
            dialog.dismiss()
        }
    }
}
