package com.alexastudillo.partyregistry.api.serialization;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/** Preserves explicit null navigation only on shared envelopes carrying complete pagination metadata. */
@Singleton
public class PartyPaginationObjectMapperCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        var module = new SimpleModule("party-pagination-cursors");
        module.setSerializerModifier(new PaginationProperties());
        objectMapper.registerModule(module);
    }

    /** Decorates only the shared envelope's two navigation properties, preserving every other library writer. */
    private static final class PaginationProperties extends BeanSerializerModifier {

        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig configuration, BeanDescription description,
                List<BeanPropertyWriter> properties) {
            if (description.getBeanClass() != ApiResponse.class) {
                return properties;
            }
            var writers = new ArrayList<>(properties);
            for (int index = 0; index < writers.size(); index++) {
                BeanPropertyWriter writer = writers.get(index);
                if (writer.getName().equals("nextCursor") || writer.getName().equals("prevCursor")) {
                    writers.set(index, new PaginationCursorWriter(writer));
                }
            }
            return writers;
        }
    }

    /** Uses complete count metadata as the pagination marker while retaining ordinary NON_NULL serialization elsewhere. */
    private static final class PaginationCursorWriter extends BeanPropertyWriter {

        @Serial
        private static final long serialVersionUID = 1L;

        private final boolean next;

        private PaginationCursorWriter(BeanPropertyWriter original) {
            super(original);
            next = original.getName().equals("nextCursor");
        }

        @Override
        public void serializeAsField(Object bean, JsonGenerator generator, SerializerProvider provider) throws Exception {
            if (bean instanceof ApiResponse<?> response
                    && response.getTotalElements() != null && response.getTotalPages() != null && response.getNumberOfElements() != null
                    && (next ? response.getNextCursor() : response.getPrevCursor()) == null) {
                generator.writeNullField(getName());
                return;
            }
            super.serializeAsField(bean, generator, provider);
        }
    }
}
