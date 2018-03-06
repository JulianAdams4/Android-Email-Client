package com.email.scenes.signup.mocks

import com.email.db.SignUpLocalDB
import com.email.db.models.User
import com.email.db.models.signal.RawSignedPreKey

/**
 * Created by sebas on 2/27/18.
 */

class MockedSignUpLocalDB : SignUpLocalDB {
    override fun storePrekeys(prekeys: Map<Int, String>) {
    }

    override fun saveUser(user: User) {
    }

    override fun storeRawSignedPrekey(rawSignedPreKey: RawSignedPreKey) {
    }

}