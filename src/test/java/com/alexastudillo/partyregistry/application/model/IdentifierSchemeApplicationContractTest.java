package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.application.command.CreateIdentifierSchemeCommand;
import com.alexastudillo.partyregistry.application.command.ChangeIdentifierSchemeLifecycleCommand;
import com.alexastudillo.partyregistry.application.command.PatchIdentifierSchemeCommand;
import com.alexastudillo.partyregistry.application.query.GetIdentifierSchemeQuery;
import com.alexastudillo.partyregistry.application.query.ListIdentifierSchemesQuery;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeChanges;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies transport-neutral request equivalence, detached output, selectors, and scoped page contracts. */
class IdentifierSchemeApplicationContractTest {

    private static final IdentifierSchemeId ID = new IdentifierSchemeId(UUID.fromString("0198d111-08f1-7e48-b291-399bbb9cd992"));
    private static final Instant CREATED = Instant.parse("2026-09-30T00:00:00Z");

    @Test
    void materializesNullableAndBooleanDefaultsWhilePreservingExactCreationValues() {
        var omitted = IdentifierSchemeCreateInput.withDefaults(" Mixed_Code ", "EC", IdentifierCategory.OTHER,
                IdentifierSubjectType.BOTH, " Mixed Name ", null, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", null, null, null);
        var explicit = input(" Mixed_Code ", " Mixed Name ", false);
        assertEquals(explicit, omitted);
        assertNotEquals(input("MIXED_CODE", " Mixed Name ", false), omitted);
        assertNotEquals(input(" Mixed_Code ", "MIXED NAME", false), omitted);
        assertNotEquals(input(" Mixed_Code ", " Mixed Name ", true), omitted);
    }

    @Test
    void creationEquivalenceExcludesCurrentActorAndProcess() {
        var firstMetadata = metadata(UUID.randomUUID(), "first");
        var secondMetadata = metadata(firstMetadata.tenantId().value(), "retry");
        var first = new CreateIdentifierSchemeCommand(firstMetadata, "key", input("CODE", "Name", false));
        var retry = new CreateIdentifierSchemeCommand(secondMetadata, "key", input("CODE", "Name", false));
        assertEquals(first.effectiveRequest(), retry.effectiveRequest());
        assertNotEquals(first.requestMetadata(), retry.requestMetadata());
    }

    @Test
    void lifecycleIdentityIncludesTargetVersionAndActionButNotRetryContext() {
        var metadata = metadata(UUID.randomUUID(), "first");
        var first = new ChangeIdentifierSchemeLifecycleCommand(metadata, ID, new IdentifierSchemeVersion(0),
                IdentifierSchemeLifecycleAction.ACTIVATE, Optional.of("key"));
        var retry = new ChangeIdentifierSchemeLifecycleCommand(metadata(metadata.tenantId().value(), "retry"), ID,
                new IdentifierSchemeVersion(0), IdentifierSchemeLifecycleAction.ACTIVATE, Optional.of("key"));
        assertEquals(first.effectiveRequest(), retry.effectiveRequest());
        assertNotEquals(first.effectiveRequest(), new IdentifierSchemeLifecycleRequest(
                IdentifierSchemeLifecycleAction.ACTIVATE, ID, new IdentifierSchemeVersion(1)));
        assertNotEquals(first.effectiveRequest(), new IdentifierSchemeLifecycleRequest(
                IdentifierSchemeLifecycleAction.RETIRE, ID, new IdentifierSchemeVersion(0)));
    }

    @Test
    void snapshotsDetachDomainDataAndPreserveOriginalAuditAttribution() {
        var original = scheme();
        var detached = IdentifierSchemeResult.fromAggregate(original);
        assertEquals(original, detached.toAggregate());
        var outcome = new IdentifierSchemeMutationOutcome(detached, IdentifierSchemeMutationOutcome.Disposition.REPLAYED);
        var completed = new CompletedIdentifierSchemeOperation(input("CODE", "Name", false), outcome.scheme());
        assertEquals("creator", completed.result().createdBy());
        assertEquals(CREATED, completed.result().updatedAt());
        assertEquals(0, completed.result().version().value());
    }

    @Test
    void pageResultsCopyInputCollectionsAndKeepCursorScopeIndependentOfGlobalOwnership() {
        var firstTenant = new TenantId(UUID.randomUUID());
        var criteria = new IdentifierSchemeSearchCriteria("EC", IdentifierCategory.OTHER,
                IdentifierSubjectType.BOTH, IdentifierSchemeStatus.DRAFT, 1);
        var firstScope = new IdentifierSchemeSearchScope(firstTenant, criteria);
        assertNotEquals(firstScope, new IdentifierSchemeSearchScope(new TenantId(UUID.randomUUID()), criteria));
        var position = new IdentifierSchemePagePosition(CREATED, ID);
        var next = new IdentifierSchemePageBoundary(IdentifierSchemePageBoundary.Direction.NEXT, position);
        var items = new ArrayList<>(List.of(IdentifierSchemeResult.fromAggregate(scheme())));
        var page = new IdentifierSchemePage(items, Optional.of("next"), Optional.empty());
        items.clear();
        var pageItems = page.items();
        assertEquals(1, pageItems.size());
        assertThrows(UnsupportedOperationException.class, pageItems::clear);
        assertEquals(position, next.position());
        var slice = new IdentifierSchemePageSlice(List.of(IdentifierSchemeResult.fromAggregate(scheme())),
                Optional.of(position), Optional.empty());
        assertEquals(1, slice.items().size());
    }

    @Test
    void exposesTypedIdAndExactCodeSelectorsAndPresenceAwareCommands() {
        var metadata = metadata(UUID.randomUUID(), "actor");
        var byId = new GetIdentifierSchemeQuery(metadata, new IdentifierSchemeSelector.ById(ID));
        var byCode = new GetIdentifierSchemeQuery(metadata, new IdentifierSchemeSelector.ByCode(" Exact_Code "));
        assertNotEquals(byId.selector(), byCode.selector());
        assertEquals(" Exact_Code ", ((IdentifierSchemeSelector.ByCode) byCode.selector()).code());
        var changes = new IdentifierSchemeChanges(FieldUpdate.present("Name"), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent());
        var patch = new PatchIdentifierSchemeCommand(metadata, ID, new IdentifierSchemeVersion(0), changes);
        assertEquals(changes, patch.changes());
        var query = new ListIdentifierSchemesQuery(metadata,
                new IdentifierSchemeSearchCriteria(null, null, null, null, 50), Optional.empty());
        assertEquals(50, query.criteria().limit());
    }

    @Test
    void rawSemanticBoundsRemainExactUntilBusinessEvaluation() {
        var huge = new BigInteger("999999999999999999999999999999999999");
        var input = new IdentifierSchemeCreateInput("CODE", "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH,
                "Name", null, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", null, huge, false);
        assertEquals(huge, input.maximumLength());
    }

    private static IdentifierSchemeCreateInput input(String code, String name, boolean expiration) {
        return new IdentifierSchemeCreateInput(code, "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH,
                name, null, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", null, null, expiration);
    }

    private static RequestMetadata metadata(UUID tenant, String actor) {
        return new RequestMetadata(new TenantId(tenant), actor, UUID.randomUUID());
    }

    private static IdentifierScheme scheme() {
        return IdentifierScheme.create(ID, "CODE", "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, "Name", null,
                "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", null, null, false, CREATED, "creator");
    }
}
