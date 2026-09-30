package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.partyregistry.api.model.request.NationalityCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.NationalityPatchRequest;
import com.alexastudillo.partyregistry.api.model.response.NationalityResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins exactly five nationality routes to reactive shared envelopes containing API DTOs. */
class NationalityResourceSignatureTest {

    @Test
    void exposesOnlyApprovedMethodsAndBodyMediaTypes() throws ReflectiveOperationException {
        assertEquals("/v1/parties", NationalityResource.class.getAnnotation(Path.class).value());
        long businessMethods = java.util.Arrays.stream(NationalityResource.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GET.class)
                        || method.isAnnotationPresent(POST.class) || method.isAnnotationPresent(PATCH.class)).count();
        assertEquals(5, businessMethods);
        Method create = NationalityResource.class.getMethod("createNationality", String.class,
                NationalityCreateRequest.class, HttpHeaders.class);
        assertTrue(create.isAnnotationPresent(POST.class));
        assertEquals("/{partyId}/nationalities", create.getAnnotation(Path.class).value());
        assertEquals(MediaType.APPLICATION_JSON, create.getAnnotation(Consumes.class).value()[0]);
        assertEquals(NationalityResponse.class, payload(create));
        Method list = NationalityResource.class.getMethod("listNationalities", String.class, UriInfo.class);
        assertTrue(list.isAnnotationPresent(GET.class));
        assertEquals("/{partyId}/nationalities", list.getAnnotation(Path.class).value());
        ParameterizedType collection = (ParameterizedType) payload(list);
        assertEquals(List.class, collection.getRawType());
        assertEquals(NationalityResponse.class, collection.getActualTypeArguments()[0]);
        Method detail = NationalityResource.class.getMethod("getNationality", String.class, String.class);
        assertTrue(detail.isAnnotationPresent(GET.class));
        assertEquals("/{partyId}/nationalities/{nationalityId}", detail.getAnnotation(Path.class).value());
        assertEquals(NationalityResponse.class, payload(detail));
        Method patch = NationalityResource.class.getMethod("patchNationality", String.class,
                String.class, NationalityPatchRequest.class);
        assertTrue(patch.isAnnotationPresent(PATCH.class));
        assertEquals(MediaType.APPLICATION_JSON, patch.getAnnotation(Consumes.class).value()[0]);
        assertEquals(NationalityResponse.class, payload(patch));
        Method primary = NationalityResource.class.getMethod("setPrimary", String.class, String.class, HttpHeaders.class);
        assertTrue(primary.isAnnotationPresent(POST.class));
        assertEquals("/{partyId}/nationalities/{nationalityId}/set-primary", primary.getAnnotation(Path.class).value());
        assertNull(primary.getAnnotation(Consumes.class));
        assertEquals(NationalityResponse.class, payload(primary));
    }

    private static Type payload(Method method) {
        ParameterizedType uni = (ParameterizedType) method.getGenericReturnType();
        assertEquals(Uni.class, uni.getRawType());
        ParameterizedType http = (ParameterizedType) uni.getActualTypeArguments()[0];
        assertEquals(RestResponse.class, http.getRawType());
        ParameterizedType envelope = (ParameterizedType) http.getActualTypeArguments()[0];
        assertEquals(ApiResponse.class, envelope.getRawType());
        return envelope.getActualTypeArguments()[0];
    }
}
