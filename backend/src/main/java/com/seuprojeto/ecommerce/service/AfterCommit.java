package com.seuprojeto.ecommerce.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Adia uma chamada externa (ex.: expirar sessão no Stripe) para depois do
 * commit da transação corrente. Chamadas que disparam webhooks precisam disso:
 * se o webhook chegar antes do commit, ele lê o estado antigo do banco.
 * Sem transação ativa (ex.: testes unitários), executa imediatamente.
 */
final class AfterCommit {

    private AfterCommit() {}

    static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
