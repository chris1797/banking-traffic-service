package com.banking.core.api

import com.banking.core.support.response.ApiResponse
import com.banking.core.support.response.error.CoreException
import com.banking.core.support.response.error.ErrorType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.logging.LogLevel
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ControllerAdvice {
    private val log: Logger = LoggerFactory.getLogger(ControllerAdvice::class.java)


    /**
     * CoreException 처리
     */
    @ExceptionHandler(CoreException::class)
    fun handleCoreException(e: CoreException): ResponseEntity<ApiResponse<Any>> {
        when (e.errorType.logLvl) {
            LogLevel.ERROR -> log.error("CoreException : {}", e.message, e)
            LogLevel.WARN -> log.warn("CoreException : {}", e.message, e)
            else -> log.info("CoreException : {}", e.message, e)
        }
        return ResponseEntity(
            ApiResponse.error(e.errorType, e.data),
            e.errorType.status
        )
    }

    /**
     * 기타 알 수 없는 예외 처리
     */
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Any>> {
        log.error("Exception : {}", e.message, e)
        return ResponseEntity(
            ApiResponse.error(ErrorType.DEFAULT_ERROR),
            ErrorType.DEFAULT_ERROR.status
        )
    }
}