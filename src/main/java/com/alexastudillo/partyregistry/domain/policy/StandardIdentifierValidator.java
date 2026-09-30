package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;

/**
 * Enumerates the explicitly supported versioned identifier validators.
 */
public enum StandardIdentifierValidator implements IdentifierValidator {
    ALPHANUMERIC_V1(new IdentifierRuleVersion(1)) {
        @Override
        public boolean isValid(String normalizedValue) {
            return normalizedValue != null && normalizedValue.matches("^[A-Z0-9]+$");
        }
    },
    EC_NATIONAL_ID_V1(new IdentifierRuleVersion(1)) {
        @Override
        public boolean isValid(String normalizedValue) {
            if (normalizedValue == null || normalizedValue.length() != 10) {
                return false;
            }
            int sum = 0;
            for (int index = 0; index < 10; index++) {
                char character = normalizedValue.charAt(index);
                if (character < '0' || character > '9') {
                    return false;
                }
                if (index < 9) {
                    int product = (character - '0') * (index % 2 == 0 ? 2 : 1);
                    sum += product > 9 ? product - 9 : product;
                }
            }
            int territorialPrefix = (normalizedValue.charAt(0) - '0') * 10
                    + normalizedValue.charAt(1) - '0';
            if ((territorialPrefix < 1 || territorialPrefix > 24) && territorialPrefix != 30) {
                return false;
            }
            return (10 - sum % 10) % 10 == normalizedValue.charAt(9) - '0';
        }
    },
    /**
     * Checks the thirteen-digit RUC structure and universal 001 suffix from RUC
     * Regulation Article 3.
     * SRI-assigned numbers do not universally follow a checksum algorithm; this
     * does not verify issuance.
     *
     * @see <a href="https://www.sri.gob.ec/facturacion-electronica">SRI warning on
     *      RUC algorithm validation</a>
     */
    EC_TAX_ID_V1(new IdentifierRuleVersion(1)) {
        @Override
        public boolean isValid(String normalizedValue) {
            return normalizedValue != null && normalizedValue.matches("\\d{10}001");
        }
    };

    private final IdentifierRuleVersion version;

    StandardIdentifierValidator(IdentifierRuleVersion version) {
        this.version = version;
    }

    @Override
    public String key() {
        return name();
    }

    @Override
    public IdentifierRuleVersion version() {
        return version;
    }
}
