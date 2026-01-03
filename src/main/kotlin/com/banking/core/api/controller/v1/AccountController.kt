package com.banking.core.api.controller.v1

import com.banking.core.dto.request.account.AccountCreateRequest
import com.banking.core.dto.request.account.AccountDepositRequest
import com.banking.core.dto.response.account.AccountResponse
import com.banking.core.service.account.AccountService
import com.banking.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AccountController(
    private val accountService: AccountService
) {

    /**
     * 계좌 생성
     */
    @PostMapping("/v1/account")
    fun createAccount(
        @RequestBody request: AccountCreateRequest
    ): ApiResponse<Any> {
        return ApiResponse.success(accountService.createAccount(request))
    }

    /**
     * 계좌 조회
     */
    @GetMapping("/v1/account/{accountNumber}")
    fun getAccount(@PathVariable accountNumber: String): ApiResponse<Any> {
        return ApiResponse.success(accountService.getAccount(accountNumber))
    }

    /**
     * 입금
     */
    @PatchMapping("/v1/account/{accountNumber}/deposit")
    fun deposit(
        @RequestBody request: AccountDepositRequest
    ): ApiResponse<AccountResponse> {
        return ApiResponse.success(accountService.deposit(request))
    }

}