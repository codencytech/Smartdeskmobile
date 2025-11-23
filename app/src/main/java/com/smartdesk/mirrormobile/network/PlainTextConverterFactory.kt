// File: PlainTextConverterFactory.kt
package com.smartdesk.mirrormobile.network

import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

class PlainTextConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        if (String::class.java == type) {
            return Converter<ResponseBody, String> { value -> value.string() }
        }
        return null
    }
}