package com.pyreview.app.data

open class ServiceException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ServiceNotConfiguredException(message: String) : ServiceException(message)

class RateLimitedException(message: String, val daily: Boolean) : ServiceException(message)
