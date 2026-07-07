package com.givewp.donationterminal.data.remote

import com.givewp.donationterminal.data.remote.dto.ApiEnvelope
import com.givewp.donationterminal.data.remote.dto.ApiErrorDto
import com.givewp.donationterminal.domain.model.AppErrorType
import com.givewp.donationterminal.domain.model.AppResult
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NetworkCallExecutorTest {

    private lateinit var executor: NetworkCallExecutor

    @Before
    fun setUp() {
        executor = NetworkCallExecutor(Moshi.Builder().build())
    }

    @Test
    fun `successful envelope unwraps to Success`() = runTest {
        val result = executor.execute {
            Response.success(ApiEnvelope(success = true, data = "hello"))
        }
        assertIs<AppResult.Success<String>>(result)
        assertEquals("hello", result.data)
    }

    @Test
    fun `envelope success false with error maps to Failure with that code`() = runTest {
        val result = executor.execute<String> {
            Response.success(
                ApiEnvelope(success = false, error = ApiErrorDto("VALIDATION_ERROR", "Amount is required"))
            )
        }
        assertIs<AppResult.Failure>(result)
        assertEquals("VALIDATION_ERROR", result.error.code)
        assertEquals(AppErrorType.VALIDATION, result.error.type)
    }

    @Test
    fun `HTTP error body is parsed into the domain error`() = runTest {
        val errorJson = """{"success":false,"error":{"code":"INVALID_CREDENTIALS","message":"Bad login"}}"""
        val result = executor.execute<String> {
            Response.error(401, errorJson.toResponseBody("application/json".toMediaType()))
        }
        assertIs<AppResult.Failure>(result)
        assertEquals("INVALID_CREDENTIALS", result.error.code)
        assertEquals(AppErrorType.AUTH, result.error.type)
        assertEquals("Bad login", result.error.message)
    }

    @Test
    fun `HTTP error with unparsable body falls back to a generic message`() = runTest {
        val result = executor.execute<String> {
            Response.error(500, "".toResponseBody("application/json".toMediaType()))
        }
        assertIs<AppResult.Failure>(result)
        assertEquals("HTTP_500", result.error.code)
        assertEquals(AppErrorType.SERVER, result.error.type)
    }
}
