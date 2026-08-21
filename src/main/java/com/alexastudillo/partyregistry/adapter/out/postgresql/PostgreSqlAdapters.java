package com.alexastudillo.partyregistry.adapter.out.postgresql;

import io.vertx.sqlclient.Pool;

/** Production and black-box-test construction seam; runtime composition remains responsible for lifecycle. */
public final class PostgreSqlAdapters {
    private PostgreSqlAdapters() {}

    public static PostgreSqlAdapterBundle create(Pool pool, PostgreSqlAdapterSettings settings) {
        return create(pool, settings, PostgreSqlTransactionObserver.noOp());
    }

    public static PostgreSqlAdapterBundle create(
            Pool pool, PostgreSqlAdapterSettings settings, PostgreSqlTransactionObserver observer) {
        PostgreSqlPartyRegistryAdapter adapter = new PostgreSqlPartyRegistryAdapter(pool, settings, observer);
        PostgreSqlIdentifierQueryAdapter identifierQueries = new PostgreSqlIdentifierQueryAdapter(adapter);
        return new PostgreSqlAdapterBundle(
                adapter, adapter, identifierQueries, adapter, adapter, adapter);
    }
}
