package com.banking.core.api.controller.v1

import com.banking.core.service.AccountService
import com.banking.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class AccountController(
    private val accountService: AccountService
) {

    @PostMapping("/v1/account")
    fun createAccount(): ApiResponse<Any> {
//        accountService.createAccount()
        return ApiResponse.success()
    }
}