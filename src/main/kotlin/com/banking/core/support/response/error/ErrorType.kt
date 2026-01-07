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
    INVALID_DEPOSIT_AMOUNT(HttpStatus.BAD_REQUEST, ErrorCode.ERROR_1004, "입금 금액이 올바르지 않습니다.", LogLevel.WARN),
    DEPOSIT_FAILED(HttpStatus.CONFLICT, ErrorCode.ERROR_1005, "입금 처리 중 충돌이 발생했습니다. 잠시 후 다시 시도해주세요.", LogLevel.WARN),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, ErrorCode.ERROR_1006, "잔액이 부족합니다.", LogLevel.WARN),
    WITHDRAW_FAILED(HttpStatus.CONFLICT, ErrorCode.ERROR_1007, "출금 처리 중 충돌이 발생했습니다. 잠시 후 다시 시도해주세요.", LogLevel.WARN),
}