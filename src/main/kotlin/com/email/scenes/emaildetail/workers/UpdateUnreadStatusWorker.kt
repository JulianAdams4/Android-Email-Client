package com.email.scenes.emaildetail.workers

import com.email.bgworker.BackgroundWorker
import com.email.db.DeliveryTypes
import com.email.db.EmailDetailLocalDB
import com.email.db.MailboxLocalDB
import com.email.scenes.emaildetail.data.EmailDetailResult
import com.email.utils.DateUtils
import org.json.JSONObject

/**
 * Created by danieltigse on 4/18/18.
 */

class UpdateUnreadStatusWorker(
        private val db: EmailDetailLocalDB,
        private val threadId: String,
        private val updateUnreadStatus: Boolean,
        override val publishFn: (EmailDetailResult.UpdateUnreadStatus) -> Unit)
    : BackgroundWorker<EmailDetailResult.UpdateUnreadStatus> {

    override val canBeParallelized = false

    override fun catchException(ex: Exception): EmailDetailResult.UpdateUnreadStatus {
        return EmailDetailResult.UpdateUnreadStatus.Failure()
    }

    override fun work(): EmailDetailResult.UpdateUnreadStatus? {
        val emailIds = db.getFullEmailsFromThreadId(threadId).map {
            it.email.id
        }
        db.updateUnreadStatus(emailIds, updateUnreadStatus)
        return EmailDetailResult.UpdateUnreadStatus.Success()
    }

    override fun cancel() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

