package com.criptext.mail.utils.generaldatasource.workers

import com.criptext.mail.R
import com.criptext.mail.api.HttpClient
import com.criptext.mail.api.HttpErrorHandlingHelper
import com.criptext.mail.api.ServerErrorException
import com.criptext.mail.api.models.DeviceInfo
import com.criptext.mail.bgworker.BackgroundWorker
import com.criptext.mail.bgworker.ProgressReporter
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.dao.AccountDao
import com.criptext.mail.db.models.Account
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.utils.generaldatasource.data.GeneralAPIClient
import com.criptext.mail.utils.generaldatasource.data.GeneralResult
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.mapError

class SyncAuthAcceptWorker(private val trustedDeviceInfo: DeviceInfo.TrustedDeviceInfo,
                           private var activeAccount: ActiveAccount,
                           private val httpClient: HttpClient,
                           private val accountDao: AccountDao,
                           private val storage: KeyValueStorage,
                           override val publishFn: (GeneralResult.SyncAccept) -> Unit
                          ) : BackgroundWorker<GeneralResult.SyncAccept> {

    override val canBeParallelized = false

    private var apiClient = GeneralAPIClient(httpClient, activeAccount.jwt)

    override fun catchException(ex: Exception): GeneralResult.SyncAccept {
        if(ex is ServerErrorException)
            return GeneralResult.SyncAccept.Failure(UIMessage(R.string.server_bad_status, arrayOf(ex.errorCode)))
        return GeneralResult.SyncAccept.Failure(UIMessage(R.string.unknown_error, arrayOf(ex.toString())))
    }

    override fun work(reporter: ProgressReporter<GeneralResult.SyncAccept>)
            : GeneralResult.SyncAccept? {

        val account = accountDao.getAccount(trustedDeviceInfo.recipientId, trustedDeviceInfo.domain) ?: return GeneralResult.SyncAccept.Failure(UIMessage(R.string.server_error_exception))
        if(account.recipientId.plus("@${account.domain}") != activeAccount.userEmail) setup(account)

        val operation = workOperation()

        val sessionExpired = HttpErrorHandlingHelper.didFailBecauseInvalidSession(operation)

        val finalResult = if(sessionExpired)
            newRetryWithNewSessionOperation()
        else
            operation

        return when (finalResult){
            is Result.Success -> {
                GeneralResult.SyncAccept.Success(activeAccount, trustedDeviceInfo.deviceId, trustedDeviceInfo.randomId, trustedDeviceInfo.deviceType)
            }
            is Result.Failure -> {
                catchException(finalResult.error)
            }
        }
    }

    override fun cancel() {
        TODO("not implemented")
    }

    private fun setup(account: Account){
        activeAccount = ActiveAccount.loadFromDB(account)!!
        apiClient = GeneralAPIClient(httpClient, activeAccount.jwt)
    }

    private fun workOperation() : Result<String, Exception> = Result.of {
        apiClient.postSyncAccept(trustedDeviceInfo.randomId).body
    }

    private fun newRetryWithNewSessionOperation()
            : Result<String, Exception> {
        val refreshOperation =  HttpErrorHandlingHelper.newRefreshSessionOperation(apiClient,
                activeAccount, storage, accountDao)
                .mapError(HttpErrorHandlingHelper.httpExceptionsToNetworkExceptions)
        return when(refreshOperation){
            is Result.Success -> {
                apiClient.token = refreshOperation.value
                workOperation()
            }
            is Result.Failure -> {
                Result.of { throw refreshOperation.error }
            }
        }
    }
}