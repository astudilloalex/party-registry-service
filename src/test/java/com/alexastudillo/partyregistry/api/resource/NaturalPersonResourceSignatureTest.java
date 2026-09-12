package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.api.model.response.NaturalPersonCreateResponse;
import com.alexastudillo.partyregistry.api.model.response.NaturalPersonResponse;
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
import java.util.Map;

import com.alexastudillo.api.response.contract.ApiResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every business method exposes the mandatory typed reactive
 * envelope.
 */
class NaturalPersonResourceSignatureTest {

    @Test
    void exposesExactlyTheFourApprovedReactiveResourceMethods() {
        Map<String, ExpectedMethod> methods = Map.of(
                "createNaturalPerson", new ExpectedMethod(POST.class, NaturalPersonCreateResponse.class),
                "getNaturalPerson", new ExpectedMethod(GET.class, NaturalPersonResponse.class),
                "replaceNaturalPerson", new ExpectedMethod(PUT.class, NaturalPersonResponse.class),
                "patchNaturalPerson", new ExpectedMethod(PATCH.class, NaturalPersonResponse.class));

        for (Map.Entry<String, ExpectedMethod> expected : methods.entrySet()) {
            Method method = findMethod(expected.getKey());
            assertTrue(method.isAnnotationPresent(expected.getValue().annotation()));
            assertMandatoryReturnType(method, expected.getValue().payloadType());
            assertNotEquals(jakarta.ws.rs.core.Response.class, method.getReturnType());
        }
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
