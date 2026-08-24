package com.theron.wallet;

import com.theron.wallet.dto.asaas.AsaasSubaccountResponse;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.request.CreateSubaccountRequest;
import com.theron.wallet.dto.request.InternalTransferRequest;
import com.theron.wallet.dto.request.WithdrawRequest;
import com.theron.wallet.entity.Customer;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.CompanyType;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;

import java.math.BigDecimal;
import java.util.UUID;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static Customer aCustomer() {
        return Customer.builder()
                .name("Test Customer")
                .email("test@therongroup.com")
                .cpfCnpj("12345678901")
                .phone("1199999999")
                .mobilePhone("11999999999")
                .asaasCustomerId("cus_" + UUID.randomUUID().toString().substring(0, 16))
                .build();
    }

    public static Customer aCustomerWithoutAsaas() {
        return Customer.builder()
                .name("Unsynced Customer")
                .email("unsynced@therongroup.com")
                .cpfCnpj("98765432100")
                .build();
    }

    public static Wallet aWallet(Subaccount subaccount) {
        return Wallet.builder()
                .subaccount(subaccount)
                .balance(BigDecimal.ZERO)
                .currency("BRL")
                .active(true)
                .build();
    }

    public static Wallet aWalletWithBalance(Subaccount subaccount, BigDecimal balance) {
        return Wallet.builder()
                .subaccount(subaccount)
                .balance(balance)
                .currency("BRL")
                .active(true)
                .build();
    }

    public static Transaction aPendingDeposit(Wallet wallet, BigDecimal amount, String asaasPaymentId) {
        return Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PROCESSING)
                .amount(amount)
                .description("Test deposit")
                .asaasPaymentId(asaasPaymentId)
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
    }

    public static Transaction aPendingWithdrawal(Wallet wallet, BigDecimal amount, String asaasTransferId) {
        return Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.PROCESSING)
                .amount(amount)
                .description("Test withdrawal")
                .asaasPaymentId(asaasTransferId)
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
    }

    public static WithdrawRequest aWithdrawRequest(UUID subaccountId, BigDecimal amount) {
        return WithdrawRequest.builder()
                .subaccountId(subaccountId)
                .amount(amount)
                .pixAddressKey("12345678901")
                .pixAddressKeyType("CPF")
                .description("Test withdrawal")
                .build();
    }

    public static InternalTransferRequest anInternalTransferRequest(
            UUID senderSubaccountId, UUID receiverSubaccountId, BigDecimal amount) {
        return InternalTransferRequest.builder()
                .senderSubaccountId(senderSubaccountId)
                .receiverSubaccountId(receiverSubaccountId)
                .amount(amount)
                .description("Test internal transfer")
                .build();
    }

    public static InternalTransferRequest anInternalTransferRequestWithKey(
            UUID senderSubaccountId, UUID receiverSubaccountId, BigDecimal amount, String idempotencyKey) {
        return InternalTransferRequest.builder()
                .senderSubaccountId(senderSubaccountId)
                .receiverSubaccountId(receiverSubaccountId)
                .amount(amount)
                .description("Test internal transfer")
                .idempotencyKey(idempotencyKey)
                .build();
    }

    public static AsaasTransferResponse anAsaasTransferResponse() {
        return AsaasTransferResponse.builder()
                .id("transfer_" + UUID.randomUUID().toString().substring(0, 12))
                .value(new BigDecimal("100.00"))
                .netValue(new BigDecimal("99.00"))
                .status("PENDING")
                .operationType("PIX")
                .description("Wallet withdrawal")
                .transferFee(new BigDecimal("1.00"))
                .build();
    }

    private static final String DEFAULT_CNPJ = "12345678000199";

    public static CreateSubaccountRequest aCreateSubaccountRequest() {
        return CreateSubaccountRequest.builder()
                .name("Test Subaccount Owner")
                .email("subaccount@therongroup.com")
                .cpfCnpj(DEFAULT_CNPJ)
                .mobilePhone("11999999999")
                .companyType(CompanyType.LIMITED)
                .incomeValue(new BigDecimal("5000.00"))
                .address("Rua Teste")
                .addressNumber("123")
                .complement("Apt 4")
                .province("São Paulo")
                .postalCode("01001000")
                .build();
    }

    /** Overload with distinct CNPJ to avoid unique constraint conflicts in tests. */
    public static CreateSubaccountRequest aCreateSubaccountRequest(String cnpj) {
        return CreateSubaccountRequest.builder()
                .name("Test Subaccount Owner")
                .email(cnpj + "@therongroup.com")
                .cpfCnpj(cnpj)
                .mobilePhone("11999999999")
                .companyType(CompanyType.LIMITED)
                .incomeValue(new BigDecimal("5000.00"))
                .address("Rua Teste")
                .addressNumber("123")
                .complement("Apt 4")
                .province("São Paulo")
                .postalCode("01001000")
                .build();
    }

    public static AsaasSubaccountResponse anAsaasSubaccountResponse() {
        return AsaasSubaccountResponse.builder()
                .id("acc_" + UUID.randomUUID().toString().substring(0, 16))
                .walletId("wal_" + UUID.randomUUID().toString().substring(0, 16))
                .apiKey("asaas_api_key_" + UUID.randomUUID().toString().substring(0, 20))
                .build();
    }

    public static AsaasSubaccountResponse anAsaasSubaccountResponseWithoutApiKey() {
        return AsaasSubaccountResponse.builder()
                .id("acc_" + UUID.randomUUID().toString().substring(0, 16))
                .walletId("wal_" + UUID.randomUUID().toString().substring(0, 16))
                .build();
    }

    public static Subaccount aSubaccount(SubaccountStatus status) {
        return Subaccount.builder()
                .name("Test Subaccount Owner")
                .email("subaccount@therongroup.com")
                .cpfCnpj(DEFAULT_CNPJ)
                .mobilePhone("11999999999")
                .companyType("LIMITED")
                .status(status)
                .incomeValue(new BigDecimal("5000.00"))
                .address("Rua Teste")
                .addressNumber("123")
                .province("São Paulo")
                .postalCode("01001000")
                .webhookToken("test-webhook-token-" + UUID.randomUUID().toString().substring(0, 8))
                .build();
    }

    /** Overload with distinct CNPJ to avoid unique constraint conflicts. */
    public static Subaccount aSubaccount(String cnpj, SubaccountStatus status) {
        return Subaccount.builder()
                .name("Test Subaccount Owner")
                .email(cnpj + "@therongroup.com")
                .cpfCnpj(cnpj)
                .mobilePhone("11999999999")
                .companyType("LIMITED")
                .status(status)
                .incomeValue(new BigDecimal("5000.00"))
                .address("Rua Teste")
                .addressNumber("123")
                .province("São Paulo")
                .postalCode("01001000")
                .webhookToken("test-webhook-token-" + UUID.randomUUID().toString().substring(0, 8))
                .build();
    }
}
