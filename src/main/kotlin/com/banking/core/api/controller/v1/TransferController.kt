package com.banking.core.api.controller.v1

import com.banking.core.dto.transfer.request.TransferRequest
import com.banking.core.dto.transfer.response.TransferResponse
import com.banking.core.service.transfer.TransferService
import com.banking.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class TransferController(
    private val transferService: TransferService
) {

    /**
     * 이체
     */
    @PostMapping("/v1/transfer")
    fun transfer(
        @RequestBody request: TransferRequest
    ): ApiResponse<TransferResponse> {
        return ApiResponse.success(transferService.transfer(request))
    }

    @GetMapping("/v1/transfer/{transferId}")
    fun getTransfer(@PathVariable transferId: Long): ApiResponse<TransferResponse> {
        return ApiResponse.success(transferService.getTransferInfo(transferId))
    }
}
