package com.criptext.mail.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * Created by gabriel on 3/21/18.
 */

interface JSONData {
    fun toJSON(): JSONObject

    fun List<JSONData>.toJSONArray(): JSONArray {
        val jsonArray = JSONArray()
        this.forEach { jsonArray.put(it.toJSON()) }
        return jsonArray
    }


}

fun List<Long>.toJSONLongArray(): JSONArray {
        val jsonArray = JSONArray()
        this.forEach { jsonArray.put(it) }
        return jsonArray
    }