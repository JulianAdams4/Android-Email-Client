package com.email.scenes.signin

import com.email.bgworker.BackgroundWorker
import com.email.bgworker.WorkHandler
import com.email.bgworker.WorkRunner
import com.email.db.SignUpLocalDB
import com.email.scenes.signup.data.RegisterUserWorker
import com.email.scenes.signup.data.SignUpAPIClient
import com.email.scenes.signup.data.SignUpRequest
import com.email.scenes.signup.data.SignUpResult

/**
 * Created by sebas on 2/15/18.
 */

class SignUpDataSource(override val runner: WorkRunner,
                       private val signUpAPIClient: SignUpAPIClient,
                       private val signUpLocalDB: SignUpLocalDB)
    : WorkHandler<SignUpRequest, SignUpResult>() {
    override fun createWorkerFromParams(params: SignUpRequest,
                                        flushResults: (SignUpResult) -> Unit):
            BackgroundWorker<*> {
        return when (params) {
            is SignUpRequest.RegisterUser -> RegisterUserWorker(signUpLocalDB,
                    signUpAPIClient,
                    user = params.user,
                    password = params.password,
                    recoveryEmail = params.recoveryEmail,
                    publishFn = { result ->
                flushResults(result)
            })
        }
    }
}