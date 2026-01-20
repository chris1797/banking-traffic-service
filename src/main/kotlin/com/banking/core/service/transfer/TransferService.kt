package com.banking.core.service.transfer

import com.banking.core.domain.TransferEntity
import com.banking.core.dto.transfer.request.TransferRequest
import com.banking.core.dto.transfer.response.TransferResponse
import com.banking.core.repository.AccountRepository
import com.banking.core.repository.TransferRepository
import com.banking.core.support.response.error.CoreException
import com.banking.core.support.response.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

@Service
class TransferService(
    private val transferRepository: TransferRepository,
    private val accountRepository: AccountRepository,
    private val transactionTemplate: TransactionTemplate
) {

    private val log = LoggerFactory.getLogger(TransferService::class.java)

    companion object {
        private const val MAX_RETRY_COUNT = 10
    }


    fun transfer(request: TransferRequest): TransferResponse {
        validateTransferRequest(request)

        val transferEntity = createPendingTransfer(request)

        return executeTransfer(transferEntity, request)
    }

    private fun validateTransferRequest(request: TransferRequest) {
        if (request.fromAccountNumber == request.toAccountNumber) {
            throw CoreException(ErrorType.SAME_ACCOUNT_TRANSFER)
        }
        if (request.amount <= BigDecimal.ZERO) {
            throw CoreException(ErrorType.INVALID_TRANSFER_AMOUNT)
        }
    }

    private fun createPendingTransfer(request: TransferRequest): TransferEntity {
        return transactionTemplate.execute {
            val transferEntity = TransferEntity.create(
                fromAccountNumber = request.fromAccountNumber,
                toAccountNumber = request.toAccountNumber,
                amount = request.amount
            )
            transferRepository.save(transferEntity)
        } ?: throw CoreException(ErrorType.TRANSFER_FAILED)
    }

    private fun executeTransfer(
        transferEntity: TransferEntity,
        request: TransferRequest
    ): TransferResponse {
        var lastException: Exception? = null

        repeat(MAX_RETRY_COUNT) { attempt ->
            try {
                return transactionTemplate.execute {
                    // Deadlock 방지: 계좌번호를 정렬하여 일관된 순서로 조회
                    val sortedAccountNumbers = listOf(request.fromAccountNumber, request.toAccountNumber).sorted()
                    val firstAccountNumber = sortedAccountNumbers[0]
                    val secondAccountNumber = sortedAccountNumbers[1]

                    val firstAccount = accountRepository.findByAccountNumber(firstAccountNumber)
                        ?: throw CoreException(ErrorType.ACCOUNT_NOT_FOUND)
                    val secondAccount = accountRepository.findByAccountNumber(secondAccountNumber)
                        ?: throw CoreException(ErrorType.ACCOUNT_NOT_FOUND)

                    // 실제 from/to 계좌 매핑
                    val fromAccount = if (firstAccountNumber == request.fromAccountNumber) firstAccount else secondAccount
                    val toAccount = if (firstAccountNumber == request.toAccountNumber) firstAccount else secondAccount

                    // 계좌 상태 검증
                    if (fromAccount.isDeleted()) {
                        throw CoreException(ErrorType.ACCOUNT_DELETED)
                    }
                    if (toAccount.isDeleted()) {
                        throw CoreException(ErrorType.ACCOUNT_DELETED)
                    }

                    // 잔액 검증
                    if (fromAccount.balance < request.amount) {
                        throw CoreException(ErrorType.INSUFFICIENT_BALANCE)
                    }

                    // 이체 실행
                    fromAccount.withdraw(request.amount)
                    toAccount.deposit(request.amount)

                    // Transfer 상태 업데이트
                    val savedTransfer = transferRepository.findById(transferEntity.id)
                        .orElseThrow { CoreException(ErrorType.TRANSFER_NOT_FOUND) }
                    savedTransfer.success()

                    TransferResponse.from(savedTransfer, fromAccount, toAccount)
                } ?: throw CoreException(ErrorType.TRANSFER_FAILED)
            } catch (e: ObjectOptimisticLockingFailureException) {
                lastException = e
                log.warn("이체 충돌 발생, 재시도 중 (from: ${request.fromAccountNumber}, to: ${request.toAccountNumber}, 시도: ${attempt + 1}/$MAX_RETRY_COUNT)")
            } catch (e: CoreException) {
                // 비즈니스 예외는 재시도하지 않고 즉시 실패 처리
                handleTransferFailure(transferEntity.id, e.errorType.message)
                throw e
            }
        }

        // 모든 재시도 실패
        handleTransferFailure(transferEntity.id, "최대 재시도 횟수 초과")
        throw CoreException(ErrorType.TRANSFER_FAILED).initCause(lastException)
    }

    private fun handleTransferFailure(transferId: Long, reason: String) {
        try {
            transactionTemplate.execute {
                val entity = transferRepository.findById(transferId).orElse(null)
                entity?.fail(reason)
            }
        } catch (e: Exception) {
            log.error("이체 실패 상태 업데이트 중 오류 발생 (transferId: $transferId)", e)
        }
    }

}
