package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.api.model.response.PartyIdentifierResponse;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins explicit outer-boundary reflection for strict identifier decoders and the safe generic response payload. */
class PartyIdentifierNativeMetadataTest {
    @Test
    void everyStrictIdentifierRequestAndItsSelectedDecoderHasExplicitNativeMetadata() {
        var bindings = Map.of(PartyIdentifierCreateRequest.class, PartyIdentifierCreateRequestDeserializer.class,
                PartyIdentifierPatchRequest.class, PartyIdentifierPatchRequestDeserializer.class,
                PartyIdentifierReasonRequest.class, PartyIdentifierReasonRequestDeserializer.class,
                PartyIdentifierPrimaryRequest.class, PartyIdentifierPrimaryRequestDeserializer.class);
        bindings.forEach((request, decoder) -> {
            assertTrue(request.isAnnotationPresent(RegisterForReflection.class));
            assertTrue(decoder.isAnnotationPresent(RegisterForReflection.class));
            assertEquals(decoder, request.getAnnotation(JsonDeserialize.class).using());
        });
    }

    @Test
    void safeIdentifierResponseIsExplicitlyRegisteredWithoutStorageOrReasonFields() {
        assertTrue(PartyIdentifierResponse.class.isRecord());
        assertTrue(PartyIdentifierResponse.class.isAnnotationPresent(RegisterForReflection.class));
        List<String> fields = java.util.Arrays.stream(PartyIdentifierResponse.class.getRecordComponents()).map(RecordComponent::getName).toList();
        for (String forbidden : List.of("encryptedValue", "normalizedValueHash", "encryptionKeyVersion", "reasonCode", "reasonDetail", "idempotencyKey")) {
            assertFalse(fields.contains(forbidden));
        }
    }
}
