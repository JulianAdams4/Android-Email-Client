package com.email.scenes.emaildetail.workers

import com.email.R
import com.email.bgworker.BackgroundWorker
import com.email.db.EmailDetailLocalDB
import com.email.scenes.emaildetail.data.EmailDetailResult
import com.email.utils.UIMessage

/**
 * Created by sebas on 3/13/18.
 */


class LoadFullEmailsFromThreadWorker(
        private val db: EmailDetailLocalDB,
        private val threadId: String,
        override val publishFn: (EmailDetailResult.LoadFullEmailsFromThreadId) -> Unit)
    : BackgroundWorker<EmailDetailResult.LoadFullEmailsFromThreadId> {

    override val canBeParallelized = false

    override fun catchException(ex: Exception): EmailDetailResult.LoadFullEmailsFromThreadId {
        val message = createErrorMessage(ex)
        return EmailDetailResult.LoadFullEmailsFromThreadId.Failure(message, ex)
    }

    override fun work(): EmailDetailResult.LoadFullEmailsFromThreadId {
        val items = db.getFullEmailsFromThreadId(threadId = threadId)
        return EmailDetailResult.LoadFullEmailsFromThreadId.Success(items)
    }

    override fun cancel() {
    }

    private val createErrorMessage: (ex: Exception) -> UIMessage = { ex ->
        UIMessage(resId = R.string.fail_register_try_again_error_exception)
    }
}
