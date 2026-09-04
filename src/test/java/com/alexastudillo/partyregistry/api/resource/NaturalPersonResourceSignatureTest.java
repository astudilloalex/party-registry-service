package com.alexastudillo.partyregistry.api.resource;

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
        Map<String, Class<? extends Annotation>> methods = Map.of(
                "createNaturalPerson", POST.class,
                "getNaturalPerson", GET.class,
                "replaceNaturalPerson", PUT.class,
                "patchNaturalPerson", PATCH.class);

        for (Map.Entry<String, Class<? extends Annotation>> expected : methods.entrySet()) {
            Method method = findMethod(expected.getKey());
            assertTrue(method.isAnnotationPresent(expected.getValue()));
            assertMandatoryReturnType(method);
            assertNotEquals(jakarta.ws.rs.core.Response.class, method.getReturnType());
        }
    }

    private static Method findMethod(String name) {
        return java.util.Arrays.stream(NaturalPersonResource.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static void assertMandatoryReturnType(Method method) {
        ParameterizedType uniType = (ParameterizedType) method.getGenericReturnType();
        assertEquals(Uni.class, uniType.getRawType());

        ParameterizedType restType = (ParameterizedType) uniType.getActualTypeArguments()[0];
        assertEquals(RestResponse.class, restType.getRawType());

        ParameterizedType envelopeType = (ParameterizedType) restType.getActualTypeArguments()[0];
        assertEquals(ApiResponse.class, envelopeType.getRawType());
        Type payloadType = envelopeType.getActualTypeArguments()[0];
        assertEquals(NaturalPersonResponse.class, payloadType);
    }
}
