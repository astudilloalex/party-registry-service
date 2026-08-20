package com.alexastudillo.partyregistry.adapter.out.postgresql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alexastudillo.partyregistry.application.port.IdentifierQueryPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeCatalogPort;
import com.alexastudillo.partyregistry.application.port.IdentifierUnitOfWorkPort;
import com.alexastudillo.partyregistry.application.port.OutboxStorePort;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import com.alexastudillo.partyregistry.application.port.PartyUnitOfWorkPort;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostgreSqlAdapterPresenceTest {
    private static final String PACKAGE = "com.alexastudillo.partyregistry.adapter.out.postgresql";

    @Test
    void postgresAdapterImplementsEveryPersistencePort() throws Exception {
        Path productionClasses = Path.of(PartyQueryPort.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path adapterPackage = productionClasses.resolve(PACKAGE.replace('.', '/'));
        List<Class<?>> productionTypes = Files.isDirectory(adapterPackage)
                ? discoverProductionTypes(productionClasses, adapterPackage)
                : List.of();

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

    private static List<Class<?>> discoverProductionTypes(Path productionClasses, Path adapterPackage)
            throws Exception {
        try (var files = Files.walk(adapterPackage)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .map(productionClasses::relativize)
                    .map(Path::toString)
                    .map(name -> name.substring(0, name.length() - 6)
                            .replace('/', '.').replace('\\', '.'))
                    .<Class<?>>map(PostgreSqlAdapterPresenceTest::load)
                    .toList();
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException failure) {
            throw new AssertionError("Cannot inspect PostgreSQL adapter type " + name, failure);
        }
    }
}
