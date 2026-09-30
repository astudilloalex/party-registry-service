package com.alexastudillo.partyregistry.architecture.fixture.isolation.domain;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.Entity;
import org.hibernate.Session;

/**
 * Represents forbidden Domain dependencies on Hibernate, JPA, and Panache.
 */
public final class DomainDependingOnPersistence {

    public PanacheEntity panacheEntity(PanacheEntity entity) {
        return entity;
    }

    public Entity entityAnnotation(Entity annotation) {
        return annotation;
    }

    public boolean isOpen(Session session) {
        return session.isOpen();
    }
}
