package com.alexastudillo.partyregistry.adapter.out.postgresql;

/**
 * Observes real reactive transaction boundaries. The completion callback runs only after the
 * transaction commit or rollback has completed, allowing integration tests and telemetry wiring to
 * detect transaction overlap without inspecting adapter internals.
 */
public interface PostgreSqlTransactionObserver {
    void opened(String operation);

    void completed(String operation);

    static PostgreSqlTransactionObserver noOp() {
        return NoOpHolder.INSTANCE;
    }

    final class NoOpHolder {
        private static final PostgreSqlTransactionObserver INSTANCE = new PostgreSqlTransactionObserver() {
            @Override
            public void opened(String operation) {}

            @Override
            public void completed(String operation) {}
        };

        private NoOpHolder() {}
    }
}
