package com.christoskarakoutis.paymentsystem.service;

import com.christoskarakoutis.paymentsystem.dto.TransactionRequest;
import com.christoskarakoutis.paymentsystem.dto.TransactionResponse;
import com.christoskarakoutis.paymentsystem.entity.Transaction;
import com.christoskarakoutis.paymentsystem.entity.TransactionStatus;
import com.christoskarakoutis.paymentsystem.entity.Wallet;
import com.christoskarakoutis.paymentsystem.exception.ResourceNotFoundException;
import com.christoskarakoutis.paymentsystem.repository.TransactionRepository;
import com.christoskarakoutis.paymentsystem.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private WalletRepository walletRepository;

    private TransactionService transactionService;

    private Wallet sourceWallet;
    private Wallet targetWallet;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transactionRepository, walletRepository);

        sourceWallet = new Wallet("src-1", "user-1", new BigDecimal("200.00"), "EUR", null, null);
        targetWallet = new Wallet("tgt-1", "user-2", new BigDecimal("50.00"), "EUR", null, null);
    }

    private TransactionRequest request(String idempotencyKey) {
        return new TransactionRequest(
                idempotencyKey, "src-1", "tgt-1",
                new BigDecimal("100.00"), "payment", null
        );
    }

    // ── executeAtomicTransfer ───────────────────────────────────────────

    @Test
    @DisplayName("returns existing transaction on idempotent request")
    void executeAtomicTransfer_returnsExistingOnIdempotentKey() {
        Transaction existing = Transaction.builder()
                .id("tx-1").idempotencyKey("dup-key")
                .sourceWallet(sourceWallet).targetWallet(targetWallet)
                .amount(new BigDecimal("100.00"))
                .status(TransactionStatus.COMPLETED)
                .build();
        when(transactionRepository.findByIdempotencyKey("dup-key")).thenReturn(Optional.of(existing));

        TransactionResponse response = transactionService.executeAtomicTransfer(request("dup-key"));

        assertThat(response.id()).isEqualTo("tx-1");
        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(walletRepository, never()).findByIdWithLock(any());
    }

    @Test
    @DisplayName("throws when source and target are the same wallet")
    void executeAtomicTransfer_throwsOnSameWallet() {
        var badRequest = new TransactionRequest(
                "key-1", "same-wallet", "same-wallet",
                new BigDecimal("100.00"), "oops", null
        );
        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.executeAtomicTransfer(badRequest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("completes transfer and updates balances")
    void executeAtomicTransfer_completesSuccessfully() {
        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(walletRepository.findByIdWithLock("src-1")).thenReturn(Optional.of(sourceWallet));
        when(walletRepository.findByIdWithLock("tgt-1")).thenReturn(Optional.of(targetWallet));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.executeAtomicTransfer(request("key-1"));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.sourceWalletId()).isEqualTo("src-1");
        assertThat(response.targetWalletId()).isEqualTo("tgt-1");
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("100.00"));

        assertThat(sourceWallet.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(targetWallet.getBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("returns FAILED when source has insufficient balance")
    void executeAtomicTransfer_returnsFailedOnInsufficientBalance() {
        sourceWallet.setBalance(new BigDecimal("10.00"));

        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(walletRepository.findByIdWithLock("src-1")).thenReturn(Optional.of(sourceWallet));
        when(walletRepository.findByIdWithLock("tgt-1")).thenReturn(Optional.of(targetWallet));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.executeAtomicTransfer(request("key-1"));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(sourceWallet.getBalance()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(targetWallet.getBalance()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("completes transfer with correct balance updates")
    void executeAtomicTransfer_updatesBalances() {
        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(walletRepository.findByIdWithLock("src-1")).thenReturn(Optional.of(sourceWallet));
        when(walletRepository.findByIdWithLock("tgt-1")).thenReturn(Optional.of(targetWallet));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.executeAtomicTransfer(request("key-1"));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(sourceWallet.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(targetWallet.getBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    // ── refundTransaction ───────────────────────────────────────────────

    @Test
    @DisplayName("creates refund with swapped wallets")
    void refundTransaction_createsRefund() {
        targetWallet.setBalance(new BigDecimal("200.00"));
        sourceWallet.setBalance(new BigDecimal("200.00"));

        Transaction original = Transaction.builder()
                .id("orig-1").idempotencyKey("orig-key")
                .sourceWallet(sourceWallet).targetWallet(targetWallet)
                .amount(new BigDecimal("100.00"))
                .status(TransactionStatus.COMPLETED)
                .build();

        when(transactionRepository.findById("orig-1")).thenReturn(Optional.of(original));
        when(transactionRepository.existsByReferenceTransactionId("orig-1")).thenReturn(false);
        when(transactionRepository.findByIdempotencyKey("REFUND-orig-key")).thenReturn(Optional.empty());
        when(walletRepository.findByIdWithLock("tgt-1")).thenReturn(Optional.of(targetWallet));
        when(walletRepository.findByIdWithLock("src-1")).thenReturn(Optional.of(sourceWallet));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.refundTransaction("orig-1");

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.sourceWalletId()).isEqualTo("tgt-1");
        assertThat(response.targetWalletId()).isEqualTo("src-1");
        assertThat(response.referenceTransactionId()).isEqualTo("orig-1");
    }

    @Test
    @DisplayName("refund throws when original is not COMPLETED")
    void refundTransaction_throwsWhenNotCompleted() {
        Transaction original = Transaction.builder()
                .id("orig-1").status(TransactionStatus.FAILED).build();
        when(transactionRepository.findById("orig-1")).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> transactionService.refundTransaction("orig-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("refund throws when already refunded")
    void refundTransaction_throwsWhenAlreadyRefunded() {
        Transaction original = Transaction.builder()
                .id("orig-1").status(TransactionStatus.COMPLETED).build();
        when(transactionRepository.findById("orig-1")).thenReturn(Optional.of(original));
        when(transactionRepository.existsByReferenceTransactionId("orig-1")).thenReturn(true);

        assertThatThrownBy(() -> transactionService.refundTransaction("orig-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── getTransaction ──────────────────────────────────────────────────

    @Test
    @DisplayName("getTransaction returns response when found")
    void getTransaction_returnsWhenFound() {
        Transaction tx = Transaction.builder()
                .id("tx-1").sourceWallet(sourceWallet).targetWallet(targetWallet)
                .amount(new BigDecimal("50.00")).status(TransactionStatus.COMPLETED)
                .build();
        when(transactionRepository.findById("tx-1")).thenReturn(Optional.of(tx));

        TransactionResponse response = transactionService.getTransaction("tx-1");

        assertThat(response.id()).isEqualTo("tx-1");
        assertThat(response.status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("getTransaction throws when not found")
    void getTransaction_throwsWhenNotFound() {
        when(transactionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransaction("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getTransactionsByWalletId ────────────────────────────────────────

    @Test
    @DisplayName("getTransactionsByWalletId returns mapped responses")
    void getTransactionsByWalletId_returnsResponses() {
        Transaction tx1 = Transaction.builder()
                .id("tx-1").sourceWallet(sourceWallet).targetWallet(targetWallet)
                .amount(new BigDecimal("50.00")).status(TransactionStatus.COMPLETED)
                .build();
        when(transactionRepository.findAllBySourceWalletIdOrTargetWalletId(
                anyString(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx1)));

        List<TransactionResponse> responses = transactionService.getTransactionsByWalletId("src-1");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo("tx-1");
    }
}
