package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.hibernate.orm.JsonFormat;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import jakarta.inject.Singleton;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;

import java.io.IOException;

/**
 * Isolates persistence JSON serialization from REST ObjectMapper
 * customizations.
 */
@Singleton
@JsonFormat
@PersistenceUnitExtension
public class PersistenceJsonFormatMapper implements FormatMapper {

    private final FormatMapper delegate = new JacksonJsonFormatMapper(
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));

    @Override
    public <T> T fromString(
            CharSequence charSequence,
            JavaType<T> javaType,
            WrapperOptions wrapperOptions) {
        return delegate.fromString(charSequence, javaType, wrapperOptions);
    }

    @Override
    public <T> String toString(
            T value,
            JavaType<T> javaType,
            WrapperOptions wrapperOptions) {
        return delegate.toString(value, javaType, wrapperOptions);
    }

    @Override
    public boolean supportsSourceType(Class<?> sourceType) {
        return delegate.supportsSourceType(sourceType);
    }

    @Override
    public boolean supportsTargetType(Class<?> targetType) {
        return delegate.supportsTargetType(targetType);
    }

    @Override
    public <T> void writeToTarget(
            T value,
            JavaType<T> javaType,
            Object target,
            WrapperOptions wrapperOptions) throws IOException {
        delegate.writeToTarget(value, javaType, target, wrapperOptions);
    }

    @Override
    public <T> T readFromSource(
            JavaType<T> javaType,
            Object source,
            WrapperOptions wrapperOptions) throws IOException {
        return delegate.readFromSource(javaType, source, wrapperOptions);
    }
}
