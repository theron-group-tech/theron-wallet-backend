package com.theron.wallet.service;

import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.ledger.LedgerEntryDraft;
import com.theron.wallet.dto.ledger.LedgerPostingRequest;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.InternalTransferRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.LedgerTransactionResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.entity.LedgerAccount;
import com.theron.wallet.entity.LedgerEntry;
import com.theron.wallet.entity.LedgerTransaction;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.LedgerAccountKind;
import com.theron.wallet.enums.LedgerDirection;
import com.theron.wallet.enums.LedgerTransactionType;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.repository.LedgerAccountRepository;
import com.theron.wallet.repository.LedgerEntryRepository;
import com.theron.wallet.repository.LedgerTransactionRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LedgerServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private InternalTransferService internalTransferService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrganizationMembershipService membershipService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private SubaccountRepository subaccountRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("1. crédito cria 2 entries e saldo do cliente igual ao amount")
    void creditCreatesBalancedEntries() throws Exception {
        AccountResponse account = createAccount("11122233000201", "Ledger credit");
        BigDecimal amount = new BigDecimal("25.50");

        LedgerTransactionResponse posted = ledgerService.postCredit(
                account.getId(), amount, "ledger:test-credit-1", account.getId().toString());

        assertThat(posted.getEntries()).hasSize(2);
        assertThat(posted.getType()).isEqualTo(LedgerTransactionType.CREDIT);

        LedgerAccount customer = customerLedger(account.getId());
        assertThat(posted.getEntries()).anySatisfy(entry -> {
            assertThat(entry.getLedgerAccountId()).isEqualTo(customer.getId());
            assertThat(entry.getDirection()).isEqualTo(LedgerDirection.CREDIT);
            assertThat(entry.getAmount()).isEqualByComparingTo(amount);
        });
        assertThat(posted.getEntries()).anySatisfy(entry ->
                assertThat(entry.getDirection()).isEqualTo(LedgerDirection.DEBIT));

        assertThat(ledgerService.reconstructBalance(account.getId())).isEqualByComparingTo(amount);

        mockMvc.perform(get("/api/v1/accounts/{id}/ledger-balance", account.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getId().toString()))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.balance").value(25.50));
    }

    @Test
    @DisplayName("2. débito após crédito reduz o saldo reconstruído")
    void debitAfterCredit() {
        AccountResponse account = createAccount("11122233000202", "Ledger debit");
        ledgerService.postCredit(account.getId(), new BigDecimal("100.00"), "ledger:test-debit-credit", null);
        ledgerService.postDebit(account.getId(), new BigDecimal("40.00"), "ledger:test-debit", null);

        assertThat(ledgerService.reconstructBalance(account.getId()))
                .isEqualByComparingTo(new BigDecimal("60.00"));
    }

    @Test
    @DisplayName("3. transferência entre duas Accounts da mesma org")
    void transferBetweenAccounts() {
        OrganizationResponse org = createOrg("11122233000203");
        AccountResponse from = accountService.create(org.getId(),
                CreateAccountRequest.builder().name("From").type(AccountType.MAIN).build());
        AccountResponse to = accountService.create(org.getId(),
                CreateAccountRequest.builder().name("To").type(AccountType.RESERVE).build());

        ledgerService.postCredit(from.getId(), new BigDecimal("80.00"), "ledger:test-transfer-fund", null);
        LedgerTransactionResponse posted = ledgerService.postTransfer(
                from.getId(), to.getId(), new BigDecimal("30.00"), "ledger:test-transfer", null);

        assertThat(posted.getType()).isEqualTo(LedgerTransactionType.TRANSFER);
        assertThat(posted.getEntries()).hasSize(2);
        assertThat(ledgerService.reconstructBalance(from.getId())).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(ledgerService.reconstructBalance(to.getId())).isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("4. SUM(DEBIT) == SUM(CREDIT) em toda transação persistida")
    void everyPersistedTransactionIsBalanced() {
        AccountResponse account = createAccount("11122233000204", "Balanced txs");
        ledgerService.postCredit(account.getId(), new BigDecimal("15.00"), "ledger:balanced-1", null);
        ledgerService.postDebit(account.getId(), new BigDecimal("5.00"), "ledger:balanced-2", null);

        List<LedgerTransaction> transactions = ledgerTransactionRepository.findAll();
        assertThat(transactions).isNotEmpty();
        for (LedgerTransaction transaction : transactions) {
            List<LedgerEntry> entries = ledgerEntryRepository.findByTransaction_Id(transaction.getId());
            assertThat(entries.size()).isGreaterThanOrEqualTo(2);
            BigDecimal debit = sum(entries, LedgerDirection.DEBIT);
            BigDecimal credit = sum(entries, LedgerDirection.CREDIT);
            assertThat(debit).isEqualByComparingTo(credit);
        }
    }

    @Test
    @DisplayName("5. posting desbalanceado lança exceção e não persiste rows")
    void unbalancedPostingPersistsNothing() {
        AccountResponse account = createAccount("11122233000205", "Unbalanced");
        LedgerAccount customer = customerLedger(account.getId());
        LedgerAccount clearing = clearing(account);

        assertThatThrownBy(() -> ledgerService.post(new LedgerPostingRequest(
                "ledger:unbalanced",
                LedgerTransactionType.CREDIT,
                null,
                List.of(
                        new LedgerEntryDraft(customer.getId(), LedgerDirection.CREDIT, new BigDecimal("10.00")),
                        new LedgerEntryDraft(clearing.getId(), LedgerDirection.DEBIT, new BigDecimal("9.00"))
                ))))
                .isInstanceOf(InvalidRequestException.class);

        assertThat(ledgerTransactionRepository.count()).isZero();
        assertThat(ledgerEntryRepository.count()).isZero();
    }

    @Test
    @DisplayName("6. amount zero é rejeitado")
    void zeroAmountRejected() {
        AccountResponse account = createAccount("11122233000206", "Zero amount");

        assertThatThrownBy(() -> ledgerService.postCredit(
                account.getId(), BigDecimal.ZERO, "ledger:zero", null))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(ledgerTransactionRepository.count()).isZero();
        assertThat(ledgerEntryRepository.count()).isZero();
    }

    @Test
    @DisplayName("7. amount negativo é rejeitado")
    void negativeAmountRejected() {
        AccountResponse account = createAccount("11122233000207", "Negative amount");

        assertThatThrownBy(() -> ledgerService.postCredit(
                account.getId(), new BigDecimal("-1.00"), "ledger:negative", null))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(ledgerTransactionRepository.count()).isZero();
        assertThat(ledgerEntryRepository.count()).isZero();
    }

    @Test
    @DisplayName("8. posts paralelos na mesma Account: saldo consistente e nunca negativo")
    void concurrentPostsStayConsistent() throws Exception {
        AccountResponse account = createAccount("11122233000208", "Concurrent");
        ledgerService.postCredit(account.getId(), new BigDecimal("10.00"), "ledger:concurrent-seed", null);

        int threadCount = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            String key = "ledger:concurrent-debit-" + i;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    ledgerService.postDebit(account.getId(), new BigDecimal("10.00"), key, null);
                    successCount.incrementAndGet();
                } catch (InsufficientBalanceException ignored) {
                    // expected for the loser
                } catch (Exception ex) {
                    if (rootCause(ex) instanceof InsufficientBalanceException) {
                        return;
                    }
                    throw new RuntimeException(ex);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        pool.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        BigDecimal reconstructed = ledgerService.reconstructBalance(account.getId());
        assertThat(reconstructed).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(reconstructed).isEqualByComparingTo(BigDecimal.ZERO);

        CountDownLatch creditStart = new CountDownLatch(1);
        CountDownLatch creditDone = new CountDownLatch(threadCount);
        ExecutorService creditPool = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> creditFutures = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            String key = "ledger:concurrent-credit-" + i;
            creditFutures.add(creditPool.submit(() -> {
                try {
                    creditStart.await();
                    ledgerService.postCredit(account.getId(), new BigDecimal("5.00"), key, null);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                } finally {
                    creditDone.countDown();
                }
            }));
        }
        creditStart.countDown();
        creditDone.await();
        creditPool.shutdown();
        for (Future<?> future : creditFutures) {
            future.get();
        }
        assertThat(ledgerService.reconstructBalance(account.getId()))
                .isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("9. rollback no meio da TX não persiste transaction nem entries")
    void rollbackPersistsNothing() {
        AccountResponse account = createAccount("11122233000209", "Rollback");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            ledgerService.postCredit(account.getId(), new BigDecimal("12.00"), "ledger:rollback", null);
            throw new IllegalStateException("forced rollback");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("forced rollback");

        assertThat(ledgerTransactionRepository.count()).isZero();
        assertThat(ledgerEntryRepository.count()).isZero();

        LedgerAccount customer = customerLedger(account.getId());
        LedgerAccount clearing = clearing(account);
        assertThatThrownBy(() -> ledgerService.post(new LedgerPostingRequest(
                "ledger:rollback-invalid-draft",
                LedgerTransactionType.CREDIT,
                null,
                List.of(
                        new LedgerEntryDraft(customer.getId(), LedgerDirection.DEBIT, new BigDecimal("10.00")),
                        new LedgerEntryDraft(clearing.getId(), LedgerDirection.CREDIT, BigDecimal.ZERO)
                ))))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(ledgerTransactionRepository.count()).isZero();
        assertThat(ledgerEntryRepository.count()).isZero();
    }

    @Test
    @DisplayName("10. duplicate idempotencyKey devolve a mesma transação sem entries extras")
    void duplicateIdempotencyKey() {
        AccountResponse account = createAccount("11122233000210", "Idempotency");
        String key = "ledger:duplicate-key";

        LedgerTransactionResponse first = ledgerService.postCredit(
                account.getId(), new BigDecimal("7.00"), key, "ref-1");
        LedgerTransactionResponse second = ledgerService.postCredit(
                account.getId(), new BigDecimal("7.00"), key, "ref-1");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(ledgerTransactionRepository.count()).isEqualTo(1);
        assertThat(ledgerEntryRepository.count()).isEqualTo(2);
        assertThat(ledgerService.reconstructBalance(account.getId()))
                .isEqualByComparingTo(new BigDecimal("7.00"));
    }

    @Test
    @DisplayName("11. entries são imutáveis: sem API de update e save não altera amount")
    void entriesAreImmutable() throws Exception {
        AccountResponse account = createAccount("11122233000211", "Immutable");
        LedgerTransactionResponse posted = ledgerService.postCredit(
                account.getId(), new BigDecimal("3.33"), "ledger:immutable", null);
        UUID entryId = posted.getEntries().getFirst().getId();
        BigDecimal original = posted.getEntries().getFirst().getAmount();

        assertThat(Arrays.stream(LedgerService.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("update") || name.startsWith("delete") || name.contains("amend"));
        assertThat(Arrays.stream(LedgerEntry.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.equals("setAmount") || name.equals("setDirection"));

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            try {
                LedgerEntry entry = entityManager.find(LedgerEntry.class, entryId);
                Field amountField = LedgerEntry.class.getDeclaredField("amount");
                amountField.setAccessible(true);
                amountField.set(entry, new BigDecimal("99.99"));
                entityManager.flush();
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(ex);
            }
        });

        LedgerEntry reloaded = ledgerEntryRepository.findById(entryId).orElseThrow();
        assertThat(reloaded.getAmount()).isEqualByComparingTo(original);
    }

    @Test
    @DisplayName("12. reconstrução após N lançamentos igual à soma algébrica esperada")
    void reconstructAfterManyPostings() {
        AccountResponse account = createAccount("11122233000212", "Reconstruct");
        ledgerService.postCredit(account.getId(), new BigDecimal("100.00"), "ledger:recon-1", null);
        ledgerService.postCredit(account.getId(), new BigDecimal("20.50"), "ledger:recon-2", null);
        ledgerService.postDebit(account.getId(), new BigDecimal("15.25"), "ledger:recon-3", null);
        ledgerService.postCredit(account.getId(), new BigDecimal("1.00"), "ledger:recon-4", null);

        BigDecimal expected = new BigDecimal("100.00")
                .add(new BigDecimal("20.50"))
                .subtract(new BigDecimal("15.25"))
                .add(new BigDecimal("1.00"));
        assertThat(ledgerService.reconstructBalance(account.getId())).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("13. precisão 0.01 + 0.10 + 10.00 sem float e scale 2")
    void decimalPrecision() {
        AccountResponse account = createAccount("11122233000213", "Precision");
        ledgerService.postCredit(account.getId(), new BigDecimal("0.01"), "ledger:prec-1", null);
        ledgerService.postCredit(account.getId(), new BigDecimal("0.10"), "ledger:prec-2", null);
        ledgerService.postCredit(account.getId(), new BigDecimal("10.00"), "ledger:prec-3", null);

        BigDecimal balance = ledgerService.reconstructBalance(account.getId());
        assertThat(balance).isEqualByComparingTo(new BigDecimal("10.11"));
        assertThat(balance.scale()).isEqualTo(2);
        assertThat(balance).isNotInstanceOf(Double.class);
    }

    @Test
    @DisplayName("comparação: WalletService credit/debit iguala reconstrução do ledger")
    void walletServiceDualWriteMatchesLedger() {
        AccountResponse account = createAccount("11122233000214", "Wallet compare");
        WalletResponse wallet = accountService.findWallet(account.getId());

        walletService.credit(wallet.getId(), new BigDecimal("0.01"));
        assertWalletMatchesLedger(account.getId());
        walletService.credit(wallet.getId(), new BigDecimal("0.10"));
        assertWalletMatchesLedger(account.getId());
        walletService.credit(wallet.getId(), new BigDecimal("10.00"));
        assertWalletMatchesLedger(account.getId());
        walletService.debit(wallet.getId(), new BigDecimal("3.11"));
        assertWalletMatchesLedger(account.getId());

        WalletResponse updated = walletService.findById(wallet.getId());
        assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("7.00"));
        assertThat(ledgerService.reconstructBalance(account.getId()))
                .isEqualByComparingTo(updated.getBalance());
    }

    @Test
    @DisplayName("comparação: transfer interna entre Account-wallets iguala reconstrução")
    void internalTransferDualWriteMatchesLedger() {
        OrganizationResponse org = createOrg("11122233000215");
        AccountResponse from = accountService.create(org.getId(),
                CreateAccountRequest.builder().name("Sender").type(AccountType.MAIN).build());
        AccountResponse to = accountService.create(org.getId(),
                CreateAccountRequest.builder().name("Receiver").type(AccountType.EMPLOYEE).build());

        Subaccount senderSub = attachSubaccount(from.getId(), "11144477735");
        Subaccount receiverSub = attachSubaccount(to.getId(), "22255588846");

        WalletResponse senderWallet = accountService.findWallet(from.getId());
        walletService.credit(senderWallet.getId(), new BigDecimal("200.00"));
        assertWalletMatchesLedger(from.getId());
        assertWalletMatchesLedger(to.getId());

        UserResponse actor = userService.create(CreateUserRequest.builder()
                .name("Ledger Transfer Actor")
                .email("ledger-transfer-actor@theron.test")
                .password("SenhaForte1!")
                .build());
        membershipService.addMember(org.getId(), AddOrganizationMemberRequest.builder()
                .userId(actor.getId())
                .build());
        roleAssignmentService.assignRolesInternal(org.getId(), actor.getId(), List.of(RoleCode.OWNER.name()));

        internalTransferService.transfer(actor.getId(), InternalTransferRequest.builder()
                .senderSubaccountId(senderSub.getId())
                .receiverSubaccountId(receiverSub.getId())
                .amount(new BigDecimal("75.50"))
                .idempotencyKey("transfer-compare-1")
                .description("Ledger compare")
                .build());

        assertWalletMatchesLedger(from.getId());
        assertWalletMatchesLedger(to.getId());
        assertThat(ledgerService.reconstructBalance(from.getId()))
                .isEqualByComparingTo(new BigDecimal("124.50"));
        assertThat(ledgerService.reconstructBalance(to.getId()))
                .isEqualByComparingTo(new BigDecimal("75.50"));
    }

    private void assertWalletMatchesLedger(UUID accountId) {
        WalletResponse wallet = accountService.findWallet(accountId);
        assertThat(ledgerService.reconstructBalance(accountId))
                .as("wallet.balance must equal reconstructed ledger balance for account %s", accountId)
                .isEqualByComparingTo(wallet.getBalance());
    }

    private Subaccount attachSubaccount(UUID accountId, String cpfCnpj) {
        Subaccount subaccount = subaccountRepository.save(
                TestFixtures.aSubaccount(cpfCnpj, SubaccountStatus.ACTIVE));
        Wallet wallet = walletRepository.findByAccount_Id(accountId).orElseThrow();
        wallet.setSubaccount(subaccount);
        walletRepository.save(wallet);
        return subaccount;
    }

    private AccountResponse createAccount(String document, String name) {
        OrganizationResponse org = createOrg(document);
        return accountService.create(org.getId(),
                CreateAccountRequest.builder().name(name).type(AccountType.MAIN).build());
    }

    private OrganizationResponse createOrg(String document) {
        return organizationService.create(CreateOrganizationRequest.builder()
                .legalName("Org " + document)
                .document(document)
                .documentType(DocumentType.CNPJ)
                .build());
    }

    private LedgerAccount customerLedger(UUID accountId) {
        return ledgerAccountRepository.findByAccount_Id(accountId).orElseThrow();
    }

    private LedgerAccount clearing(AccountResponse account) {
        return ledgerAccountRepository
                .findByOrganization_IdAndKindAndCurrency(
                        account.getOrganizationId(), LedgerAccountKind.CLEARING, "BRL")
                .orElseThrow();
    }

    private static BigDecimal sum(List<LedgerEntry> entries, LedgerDirection direction) {
        return entries.stream()
                .filter(entry -> entry.getDirection() == direction)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
