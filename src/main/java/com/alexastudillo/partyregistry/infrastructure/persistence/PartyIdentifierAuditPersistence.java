package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionAudit;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.query.TypedParameterValue;
import org.hibernate.type.StandardBasicTypes;

/** Appends independently durable private withdrawal audit inside the caller-owned reactive transaction. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyIdentifierAuditPersistence {

    private static final String INSERT = """
            INSERT INTO party_identifier_action_audit
                (tenant_id, party_id, identifier_id, action, resulting_version, actor, occurred_at, reason_code, reason_detail)
            VALUES (:tenant, :party, :identifier, :action, :version, :actor, :instant, :code, :detail)
            """;

    Uni<Void> recordAudit(Mutiny.Session session, PartyIdentifierActionAudit audit) {
        return session.createNativeQuery(INSERT)
                .setParameter("tenant", audit.requestMetadata().tenantId().value()).setParameter("party", audit.partyId().value())
                .setParameter("identifier", audit.identifierId().value()).setParameter("action", audit.operation().name())
                .setParameter("version", audit.resultingVersion().value()).setParameter("actor", audit.requestMetadata().userId())
                .setParameter("instant", audit.occurredAt())
                .setParameter("code", new TypedParameterValue<>(StandardBasicTypes.BINARY, PartyIdentifierReasonCodec.encode(audit.reason().reasonCode(), 64)))
                .setParameter("detail", new TypedParameterValue<>(StandardBasicTypes.BINARY, PartyIdentifierReasonCodec.encode(audit.reason().reasonDetail(), 1000)))
                .executeUpdate().invoke(count -> {
                    if (count != 1) { throw new IllegalStateException("Identifier audit insert count is invalid"); }
                }).replaceWithVoid();
    }
}
