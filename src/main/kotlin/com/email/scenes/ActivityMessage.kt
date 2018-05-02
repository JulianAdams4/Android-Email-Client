package com.email.scenes

import com.email.scenes.composer.data.ComposerInputData

/**
 * Created by gabriel on 4/3/18.
 */

sealed class ActivityMessage {
    data class SendMail(val emailId: Long, val threadId: String?, val composerInputData: ComposerInputData): ActivityMessage()
}