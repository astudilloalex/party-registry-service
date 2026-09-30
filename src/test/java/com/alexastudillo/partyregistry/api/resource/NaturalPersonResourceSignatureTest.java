package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.api.model.response.NaturalPersonCreateResponse;
import com.alexastudillo.partyregistry.api.model.response.NaturalPersonDetailResponse;
import com.alexastudillo.partyregistry.api.model.response.NaturalPersonDetailsResponse;
import com.alexastudillo.partyregistry.api.model.response.NaturalPersonResponse;
import com.alexastudillo.partyregistry.api.model.response.PartyIdentifierResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.alexastudillo.api.response.contract.ApiResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies typed reactive resource envelopes and the immutable GET-only detail payload.
 */
class NaturalPersonResourceSignatureTest {

    @Test
    void exposesExactlyTheFourApprovedReactiveResourceMethods() {
        Map<String, ExpectedMethod> methods = Map.of(
                "createNaturalPerson", new ExpectedMethod(POST.class, NaturalPersonCreateResponse.class),
                "getNaturalPerson", new ExpectedMethod(GET.class, NaturalPersonDetailResponse.class),
                "replaceNaturalPerson", new ExpectedMethod(PUT.class, NaturalPersonResponse.class),
                "patchNaturalPerson", new ExpectedMethod(PATCH.class, NaturalPersonResponse.class));

        for (Map.Entry<String, ExpectedMethod> expected : methods.entrySet()) {
            Method method = findMethod(expected.getKey());
            assertTrue(method.isAnnotationPresent(expected.getValue().annotation()));
            assertMandatoryReturnType(method, expected.getValue().payloadType());
            assertNotEquals(jakarta.ws.rs.core.Response.class, method.getReturnType());
        }
    }

    @Test
    void preservesFlatBaseFieldsAndRequiresATypedImmutableIdentifierList() throws ReflectiveOperationException {
        assertTrue(NaturalPersonDetailResponse.class.isRecord());
        assertEquals(NaturalPersonResponse.class.getRecordComponents().length + 1,
                NaturalPersonDetailResponse.class.getRecordComponents().length);
        for (var field : NaturalPersonResponse.class.getRecordComponents()) {
            assertEquals(field.getGenericType(),
                    NaturalPersonDetailResponse.class.getMethod(field.getName()).getGenericReturnType());
        }
        ParameterizedType identifiersType = (ParameterizedType) NaturalPersonDetailResponse.class
                .getMethod("identifiers").getGenericReturnType();
        assertEquals(List.class, identifiersType.getRawType());
        assertEquals(PartyIdentifierResponse.class, identifiersType.getActualTypeArguments()[0]);

        UUID partyId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        PartyIdentifierResponse identifier = new PartyIdentifierResponse(
                UUID.randomUUID(), partyId, UUID.randomUUID(), "TEST_NATURAL_ACTIVE", "****1234",
                "PENDING_VERIFICATION", true, null, null, null, null, null, 0, now, now);
        List<PartyIdentifierResponse> mutableIdentifiers = new ArrayList<>(List.of(identifier));
        NaturalPersonDetailResponse detail = new NaturalPersonDetailResponse(
                partyId, "NATURAL_PERSON", "Detail Person", "DRAFT", 0, now, now, "test", "test",
                new NaturalPersonDetailsResponse("Detail", "Person", null, null, null, null), mutableIdentifiers);
        mutableIdentifiers.clear();
        var detailIdentifiers = detail.identifiers();
        assertEquals(List.of(identifier), detailIdentifiers);
        assertThrows(UnsupportedOperationException.class, detailIdentifiers::clear);
    }

    private static Method findMethod(String name) {
        return java.util.Arrays.stream(NaturalPersonResource.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static void assertMandatoryReturnType(Method method, Class<?> expectedPayloadType) {
        ParameterizedType uniType = (ParameterizedType) method.getGenericReturnType();
        assertEquals(Uni.class, uniType.getRawType());

        ParameterizedType restType = (ParameterizedType) uniType.getActualTypeArguments()[0];
        assertEquals(RestResponse.class, restType.getRawType());

        ParameterizedType envelopeType = (ParameterizedType) restType.getActualTypeArguments()[0];
        assertEquals(ApiResponse.class, envelopeType.getRawType());
        Type payloadType = envelopeType.getActualTypeArguments()[0];
        assertEquals(expectedPayloadType, payloadType);
    }

    /**
     * Couples one resource annotation with its exact API payload type.
     */
    private record ExpectedMethod(
            Class<? extends Annotation> annotation,
            Class<?> payloadType) {
    }
}
