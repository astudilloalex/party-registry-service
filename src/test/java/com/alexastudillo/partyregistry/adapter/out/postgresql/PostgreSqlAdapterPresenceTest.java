package com.alexastudillo.partyregistry.adapter.out.postgresql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alexastudillo.partyregistry.application.port.IdentifierQueryPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeCatalogPort;
import com.alexastudillo.partyregistry.application.port.IdentifierUnitOfWorkPort;
import com.alexastudillo.partyregistry.application.port.OutboxStorePort;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import com.alexastudillo.partyregistry.application.port.PartyUnitOfWorkPort;
import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostgreSqlAdapterPresenceTest {
    private static final String PACKAGE = "com.alexastudillo.partyregistry.adapter.out.postgresql";

    @Test
    void postgresAdapterImplementsEveryPersistencePort() throws Exception {
        URL directoryUrl = Thread.currentThread().getContextClassLoader()
                .getResource(PACKAGE.replace('.', '/'));
        assertTrue(directoryUrl != null, "PostgreSQL adapter package is absent");
        File directory = new File(directoryUrl.toURI());
        List<Class<?>> productionTypes = Arrays.stream(directory.listFiles((ignored, name) -> name.endsWith(".class")))
                .map(file -> file.getName().substring(0, file.getName().length() - 6))
                .filter(name -> !name.contains("Test") && !name.contains("Support"))
                .<Class<?>>map(name -> load(PACKAGE + "." + name))
                .toList();

        for (Class<?> port : List.of(
                PartyQueryPort.class,
                PartyUnitOfWorkPort.class,
                IdentifierQueryPort.class,
                IdentifierUnitOfWorkPort.class,
                IdentifierSchemeCatalogPort.class,
                OutboxStorePort.class)) {
            assertTrue(productionTypes.stream().anyMatch(port::isAssignableFrom),
                    () -> "PostgreSQL adapter implementation is absent for " + port.getSimpleName());
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException failure) {
            throw new AssertionError("Cannot inspect PostgreSQL adapter type " + name, failure);
        }
    }
}
