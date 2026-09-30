package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.partyregistry.api.model.request.LegalEntityCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.PartyUpdateRequest;
import com.alexastudillo.partyregistry.api.model.response.LegalEntityCreateResponse;
import com.alexastudillo.partyregistry.api.model.response.PartyDetailResponse;
import com.alexastudillo.partyregistry.api.model.response.PartySummaryResponse;
import com.alexastudillo.partyregistry.api.model.response.PartyIdentifierResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins exact paths, parameters, and reactive envelopes for the new Party operations.
 */
class PartyResourceSignatureTest {

    @Test
    void exposesLegalEntityCreationWithItsCreateOnlyDto() throws ReflectiveOperationException {
        assertEquals("/v1/legal-entity", LegalEntityResource.class.getAnnotation(Path.class).value());
        Method method = LegalEntityResource.class.getMethod(
                "createLegalEntity",
                LegalEntityCreateRequest.class,
                HttpHeaders.class);

        assertPostEnvelope(method, LegalEntityCreateResponse.class);
    }

    @Test
    void exposesAdditionalIdentifierRegistrationWithOnlyTheSafeDto() throws ReflectiveOperationException {
        assertEquals(
                "/v1/parties/{partyId}/identifiers",
                PartyIdentifierResource.class.getAnnotation(Path.class).value());
        Method method = PartyIdentifierResource.class.getMethod(
                "createPartyIdentifier",
                String.class,
                PartyIdentifierCreateRequest.class,
                HttpHeaders.class);

        assertPostEnvelope(method, PartyIdentifierResponse.class);
    }

    @Test
    void exposesActivationWithTheSharedExhaustivePartyDto() throws ReflectiveOperationException {
        assertEquals("/v1/parties", PartyResource.class.getAnnotation(Path.class).value());
        Method method = PartyResource.class.getMethod("activateParty", String.class, HttpHeaders.class);

        assertEquals("/{partyId}/activate", method.getAnnotation(Path.class).value());
        assertPostEnvelope(method, PartyDetailResponse.class);
    }

    private static void assertPostEnvelope(Method method, Class<?> expectedPayloadType) {
        assertTrue(method.isAnnotationPresent(POST.class));
        assertEquals(expectedPayloadType, payloadType(method));
    }

    @Test
    void exposesBothRootReadsWithExplicitDtoEnvelopes() throws ReflectiveOperationException {
        Method detail = PartyResource.class.getMethod("getParty", String.class);
        assertTrue(detail.isAnnotationPresent(GET.class));
        assertEquals("/{partyId}", detail.getAnnotation(Path.class).value());
        assertEquals(PartyDetailResponse.class, payloadType(detail));
        Method list = PartyResource.class.getMethod("listParties", UriInfo.class);
        assertTrue(list.isAnnotationPresent(GET.class));
        ParameterizedType listType = (ParameterizedType) payloadType(list);
        assertEquals(List.class, listType.getRawType());
        assertEquals(PartySummaryResponse.class, listType.getActualTypeArguments()[0]);
    }

    private static Type payloadType(Method method) {
        assertNotEquals(jakarta.ws.rs.core.Response.class, method.getReturnType());
        ParameterizedType uniType = (ParameterizedType) method.getGenericReturnType();
        assertEquals(Uni.class, uniType.getRawType());
        ParameterizedType restType = (ParameterizedType) uniType.getActualTypeArguments()[0];
        assertEquals(RestResponse.class, restType.getRawType());
        ParameterizedType envelopeType = (ParameterizedType) restType.getActualTypeArguments()[0];
        assertEquals(ApiResponse.class, envelopeType.getRawType());
        return envelopeType.getActualTypeArguments()[0];
    }

    @Test
    void exposesRootCorrectionAndBothAdditionalLifecycleActionsWithDetailEnvelopes() throws ReflectiveOperationException {
        Method patch = PartyResource.class.getMethod("patchParty", String.class, PartyUpdateRequest.class, HttpHeaders.class);
        assertTrue(patch.isAnnotationPresent(PATCH.class));
        assertEquals("/{partyId}", patch.getAnnotation(Path.class).value());
        assertEquals(List.of(MediaType.APPLICATION_JSON), List.of(patch.getAnnotation(Consumes.class).value()));
        assertEquals(PartyDetailResponse.class, payloadType(patch));
        for (String action : List.of("deactivate", "archive")) {
            Method method = PartyResource.class.getMethod(action + "Party", String.class, HttpHeaders.class);
            assertEquals("/{partyId}/" + action, method.getAnnotation(Path.class).value());
            assertPostEnvelope(method, PartyDetailResponse.class);
        }
    }
}
