package com.banking.core.support.response.error

import org.springframework.boot.logging.LogLevel
import org.springframework.http.HttpStatus

enum class ErrorType(
    val status: HttpStatus,
    val code: ErrorCode,
    val message: String,
    val logLvl: LogLevel,
) {

    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.ERROR_500, "알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", LogLevel.ERROR),

    // Account Errors
    CREATE_ACCOUNT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.ERROR_1001, "계좌 생성에 실패했습니다. 잠시 후 다시 시도해주세요.", LogLevel.ERROR),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.ERROR_1002, "요청하신 계좌를 찾을 수 없습니다.", LogLevel.WARN),
    ACCOUNT_DELETED(HttpStatus.NOT_FOUND, ErrorCode.ERROR_1003, "요청하신 계좌는 삭제된 계좌입니다.", LogLevel.WARN),
}