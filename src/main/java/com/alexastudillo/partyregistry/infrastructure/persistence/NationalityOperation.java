package com.alexastudillo.partyregistry.infrastructure.persistence;

/** Separates replay-key identity and snapshot format for creation and primary designation. */
enum NationalityOperation {
    CREATE("nationality.create.v1", (short) 4),
    SET_PRIMARY("nationality.set-primary.v1", (short) 5);

    private final String label;
    private final short schemaVersion;

    NationalityOperation(String label, short schemaVersion) {
        this.label = label;
        this.schemaVersion = schemaVersion;
    }

    String label() {
        return label;
    }

    short schemaVersion() {
        return schemaVersion;
    }
}
