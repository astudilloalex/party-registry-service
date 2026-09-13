package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.partyregistry.api.model.request.LegalEntityCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierCreateRequest;
import com.alexastudillo.partyregistry.api.model.response.LegalEntityCreateResponse;
import com.alexastudillo.partyregistry.api.model.response.PartyDetailResponse;
import com.alexastudillo.partyregistry.api.model.response.PartyIdentifierResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.HttpHeaders;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

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
        assertEquals("/v1/parties/{partyId}", PartyResource.class.getAnnotation(Path.class).value());
        Method method = PartyResource.class.getMethod("activateParty", String.class, HttpHeaders.class);

        assertEquals("/activate", method.getAnnotation(Path.class).value());
        assertPostEnvelope(method, PartyDetailResponse.class);
    }

    private static void assertPostEnvelope(Method method, Class<?> expectedPayloadType) {
        assertTrue(method.isAnnotationPresent(POST.class));
        assertNotEquals(jakarta.ws.rs.core.Response.class, method.getReturnType());
        ParameterizedType uniType = (ParameterizedType) method.getGenericReturnType();
        assertEquals(Uni.class, uniType.getRawType());
        ParameterizedType restType = (ParameterizedType) uniType.getActualTypeArguments()[0];
        assertEquals(RestResponse.class, restType.getRawType());
        ParameterizedType envelopeType = (ParameterizedType) restType.getActualTypeArguments()[0];
        assertEquals(ApiResponse.class, envelopeType.getRawType());
        Type payloadType = envelopeType.getActualTypeArguments()[0];
        assertEquals(expectedPayloadType, payloadType);
    }
}
