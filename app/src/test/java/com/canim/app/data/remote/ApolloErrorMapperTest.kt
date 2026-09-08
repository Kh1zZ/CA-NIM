package com.canim.app.data.remote

import com.apollographql.apollo.api.Error
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.exception.ApolloParseException
import com.canim.app.data.remote.anilist.ApolloErrorMapper
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class ApolloErrorMapperTest {

    @Before
    fun setUp() {
        AniListMetrics.reset()
    }

    @After
    fun tearDown() {
        AniListMetrics.reset()
    }

    @Test
    fun testHttp404MapsToNotFound() {
        val exception = ApolloHttpException(
            statusCode = 404,
            headers = emptyList(),
            body = null,
            message = "Not Found",
            cause = null
        )
        val result = ApolloErrorMapper.toAniListResult(exception)
        assertTrue(result is AniListResult.NotFound)
    }

    @Test
    fun testHttp429MapsToRateLimitedAndRecordsMetric() {
        val exception = ApolloHttpException(
            statusCode = 429,
            headers = emptyList(),
            body = null,
            message = "Too Many Requests",
            cause = null
        )
        val result = ApolloErrorMapper.toAniListResult(exception)
        assertTrue(result is AniListResult.RateLimited)
        assertEquals(60L, (result as AniListResult.RateLimited).retryAfterSeconds)
        assertEquals(1L, AniListMetrics.rateLimitCount)
    }

    @Test
    fun testHttp5xxMapsToHttpErrorAndRecordsMetric() {
        val exception = ApolloHttpException(
            statusCode = 503,
            headers = emptyList(),
            body = null,
            message = "Service Unavailable",
            cause = null
        )
        val result = ApolloErrorMapper.toAniListResult(exception)
        assertTrue(result is AniListResult.HttpError)
        assertEquals(503, (result as AniListResult.HttpError).code)
        assertEquals(1L, AniListMetrics.http5xxCount)
    }

    @Test
    fun testHttp400MapsToHttpErrorWithout5xxMetric() {
        val exception = ApolloHttpException(
            statusCode = 400,
            headers = emptyList(),
            body = null,
            message = "Bad Request",
            cause = null
        )
        val result = ApolloErrorMapper.toAniListResult(exception)
        assertTrue(result is AniListResult.HttpError)
        assertEquals(400, (result as AniListResult.HttpError).code)
        assertEquals(0L, AniListMetrics.http5xxCount)
    }

    @Test
    fun testNetworkExceptionWithTimeoutCauseMapsToTimeoutAndRecordsMetric() {
        val timeoutCause = SocketTimeoutException("Read timed out")
        val exception = ApolloNetworkException("Network error", timeoutCause)
        val result = ApolloErrorMapper.toAniListResult(exception)
        assertTrue(result is AniListResult.Timeout)
        assertTrue((result as AniListResult.Timeout).isReadTimeout)
        assertEquals(1L, AniListMetrics.timeoutCount)
    }

    @Test
    fun testNetworkExceptionGenericMapsToNetworkError() {
        val exception = ApolloNetworkException("Connection reset", IOException("Connection reset"))
        val result = ApolloErrorMapper.toAniListResult(exception)
        assertTrue(result is AniListResult.NetworkError)
    }

    @Test
    fun testApolloParseExceptionWithTimeoutCauseMapsToTimeout() {
        val exception = ApolloParseException("Generic apollo error", SocketTimeoutException("timeout"))
        val result = ApolloErrorMapper.toAniListResult(exception)
        assertTrue(result is AniListResult.Timeout)
        assertEquals(1L, AniListMetrics.timeoutCount)
    }

    @Test
    fun testDirectSocketTimeoutExceptionMapsToTimeout() {
        val result = ApolloErrorMapper.toAniListResult(SocketTimeoutException("timeout"))
        assertTrue(result is AniListResult.Timeout)
        assertEquals(1L, AniListMetrics.timeoutCount)
    }

    @Test(expected = CancellationException::class)
    fun testCancellationExceptionIsRethrown() {
        ApolloErrorMapper.toAniListResult(CancellationException("Job cancelled"))
    }

    @Test
    fun testHandleGraphQLErrorsNotFound() {
        val error = Error.Builder(message = "Not Found.").build()
        val result = ApolloErrorMapper.handleGraphQLErrors(listOf(error))
        assertNotNull(result)
        assertTrue(result is AniListResult.NotFound)
    }

    @Test
    fun testHandleGraphQLErrorsGeneric() {
        val error = Error.Builder(message = "Field syntax error").build()
        val result = ApolloErrorMapper.handleGraphQLErrors(listOf(error))
        assertNotNull(result)
        assertTrue(result is AniListResult.GraphQLError)
        assertEquals(1L, AniListMetrics.graphQLErrorCount)
    }

    @Test
    fun testHandleGraphQLErrorsEmptyReturnsNull() {
        assertNull(ApolloErrorMapper.handleGraphQLErrors(null))
        assertNull(ApolloErrorMapper.handleGraphQLErrors(emptyList()))
    }

    @Test
    fun testSafeApolloCallSuccess() {
        val result = ApolloErrorMapper.safeApolloCall {
            AniListResult.Success("Hello")
        }
        assertTrue(result is AniListResult.Success)
        assertEquals("Hello", (result as AniListResult.Success).data)
    }

    @Test
    fun testSafeApolloCallCatchesAndMapsException() {
        val result = ApolloErrorMapper.safeApolloCall<String> {
            throw ApolloHttpException(
                statusCode = 404,
                headers = emptyList(),
                body = null,
                message = "Not Found",
                cause = null
            )
        }
        assertTrue(result is AniListResult.NotFound)
    }

    @Test(expected = CancellationException::class)
    fun testSafeApolloCallRethrowsCancellation() {
        ApolloErrorMapper.safeApolloCall<String> {
            throw CancellationException("Cancelled")
        }
    }
}
