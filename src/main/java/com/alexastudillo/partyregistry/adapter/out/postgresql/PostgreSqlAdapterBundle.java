package com.alexastudillo.partyregistry.adapter.out.postgresql;

import com.alexastudillo.partyregistry.application.port.IdentifierQueryPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeCatalogPort;
import com.alexastudillo.partyregistry.application.port.IdentifierUnitOfWorkPort;
import com.alexastudillo.partyregistry.application.port.OutboxStorePort;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import com.alexastudillo.partyregistry.application.port.PartyUnitOfWorkPort;

/** Application-port views created by the stable PostgreSQL construction seam. */
public record PostgreSqlAdapterBundle(
        PartyQueryPort partyQueries,
        PartyUnitOfWorkPort partyUnitOfWork,
        IdentifierQueryPort identifierQueries,
        IdentifierUnitOfWorkPort identifierUnitOfWork,
        IdentifierSchemeCatalogPort identifierSchemes,
        OutboxStorePort outboxStore) {}
