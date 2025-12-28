package com.banking.core.api.controller.v1

import com.banking.core.dto.request.AccountCreateRequest
import com.banking.core.service.account.AccountService
import com.banking.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AccountController(
    private val accountService: AccountService
) {

    @PostMapping("/v1/account")
    fun createAccount(
        @RequestBody request: AccountCreateRequest
    ): ApiResponse<Any> {
        return ApiResponse.success(accountService.createAccount(request))
    }
}