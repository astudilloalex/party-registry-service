package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.InitialPartyIdentifierInput;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierProtectionRequest;
import com.alexastudillo.partyregistry.application.port.IdentifierProtectionPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeRepository;
import com.alexastudillo.partyregistry.application.support.UuidV7;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleResult;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSchemePolicy;
import io.smallrye.mutiny.Uni;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Applies the shared eligibility, rule, and protection workflow for new Party identifiers.
 */
final class PartyIdentifierPreparation {

    private final IdentifierSchemeRepository schemeRepository;
    private final IdentifierRuleCatalog ruleCatalog;
    private final IdentifierSchemePolicy schemePolicy;
    private final IdentifierProtectionPort protectionPort;

    PartyIdentifierPreparation(
            IdentifierSchemeRepository schemeRepository,
            IdentifierRuleCatalog ruleCatalog,
            IdentifierSchemePolicy schemePolicy,
            IdentifierProtectionPort protectionPort) {
        this.schemeRepository = Objects.requireNonNull(schemeRepository, "schemeRepository");
        this.ruleCatalog = Objects.requireNonNull(ruleCatalog, "ruleCatalog");
        this.schemePolicy = Objects.requireNonNull(schemePolicy, "schemePolicy");
        this.protectionPort = Objects.requireNonNull(protectionPort, "protectionPort");
    }

    Uni<IdentifierScheme> findEligibleScheme(String schemeCode, PartyType partyType) {
        return schemeRepository.findByCode(schemeCode)
                .map(resolved -> resolved.orElseThrow(() -> new ApplicationException(
                        new ApplicationFailure.UnknownIdentifierScheme(schemeCode))))
                .map(scheme -> requireEligible(scheme, partyType));
    }

    PartyIdentifier createIdentifier(
            TenantId tenantId,
            PartyId partyId,
            IdentifierScheme scheme,
            InitialPartyIdentifierInput input,
            LocalDate evaluatedOn,
            Instant occurredAt,
            String createdBy) {
        try {
            IdentifierRuleResult rules = ruleCatalog.evaluate(scheme, input.value());
            schemePolicy.validateNormalizedLength(scheme, rules.normalizedValue());
            PartyIdentifier.validateRegistrationMetadata(
                    input.issuerCode(),
                    input.issuedOn(),
                    input.expiresOn());
            schemePolicy.validateExpiration(scheme, input.expiresOn(), evaluatedOn);

            PartyIdentifierId identifierId = new PartyIdentifierId(UuidV7.generate(occurredAt));
            var protectedValue = protectionPort.protect(new IdentifierProtectionRequest(
                    tenantId,
                    partyId,
                    identifierId,
                    scheme.id(),
                    input.value(),
                    rules.normalizedValue(),
                    rules.normalizationVersion()));
            return PartyIdentifier.create(
                    identifierId,
                    tenantId,
                    partyId,
                    scheme.id(),
                    protectedValue,
                    input.issuerCode(),
                    input.issuedOn(),
                    input.expiresOn(),
                    input.isPrimary(),
                    occurredAt,
                    createdBy);
        } catch (DomainValidationException exception) {
            throw identifierFailure(exception);
        }
    }

    private IdentifierScheme requireEligible(IdentifierScheme scheme, PartyType partyType) {
        try {
            schemePolicy.requireRegistrationEligibility(scheme, partyType);
            return scheme;
        } catch (DomainValidationException exception) {
            throw switch (exception.violation()) {
                case IDENTIFIER_SCHEME_INACTIVE -> new ApplicationException(
                        new ApplicationFailure.InactiveIdentifierScheme(scheme.id()),
                        exception);
                case IDENTIFIER_SCHEME_INCOMPATIBLE -> new ApplicationException(
                        new ApplicationFailure.IncompatibleIdentifierScheme(scheme.id(), partyType),
                        exception);
                default -> identifierFailure(exception);
            };
        }
    }

    private static ApplicationException identifierFailure(DomainValidationException exception) {
        DomainViolation violation = exception.violation();
        return violation == DomainViolation.IDENTIFIER_RULE_CATALOG_INVALID
                ? new ApplicationException(new ApplicationFailure.IdentifierCatalogFailure(), exception)
                : new ApplicationException(
                        new ApplicationFailure.IdentifierValidationFailure(violation),
                        exception);
    }
}
