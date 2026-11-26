// Written by Alaabrine, with assistance from Cursor Agent.

package com.github.se.amber.data.media

import com.google.firebase.storage.StorageException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Whether this error, or one of its causes, is a [connection error][isConnectionError]. */
internal fun Throwable.causedByConnection(): Boolean =
    generateSequence(this) { it.cause }.any { it.isConnectionError() }

/** Whether this error itself, without its causes, comes from the connection. */
private fun Throwable.isConnectionError(): Boolean =
    when (this) {
      // Firebase Storage gave up retrying a request that got no HTTP answer at all. A request that
      // got one (404, 503…) has another code, even after Firebase's retries.
      is StorageException -> isRecoverableException
      // The platform's network errors. Firebase gives them as the cause of its StorageException,
      // also when the connection is cut while the file is being read, with another code.
      is UnknownHostException,
      is SocketException,
      is SocketTimeoutException -> true
      else -> false
    }
