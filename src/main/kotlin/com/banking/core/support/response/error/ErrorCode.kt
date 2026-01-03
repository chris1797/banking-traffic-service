package com.banking.core.support.response.error

enum class ErrorCode {

    ERROR_500, // Internal Server Error
    ERROR_404, // Not Found
    ERROR_400, // Bad Request
    ERROR_401, // Unauthorized

    ERROR_1001, // Account Creation Failed
    ERROR_1002, // Account Not Found
    ERROR_1003, // Account Deleted
    ERROR_1004, // Invalid Deposit Amount
    ERROR_1005, // Deposit Failed (Optimistic Lock Conflict)
}
