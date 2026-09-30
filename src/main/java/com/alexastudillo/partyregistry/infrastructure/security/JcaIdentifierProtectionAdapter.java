package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierProtectionRequest;
import com.alexastudillo.partyregistry.application.port.IdentifierProtectionPort;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Protects official identifiers with tenant-isolated HMAC and AES-256-GCM.
 */
@ApplicationScoped
public class JcaIdentifierProtectionAdapter implements IdentifierProtectionPort {

    private static final String DEPENDENCY_NAME = "identifier-protection";
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String ENVELOPE_VERSION = "v1";
    private static final String PROTECTION_FAILURE = "Identifier protection failed";
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int AUTHENTICATION_TAG_LENGTH_BITS = 128;
    private static final int AUTHENTICATION_TAG_LENGTH_BYTES = AUTHENTICATION_TAG_LENGTH_BITS / Byte.SIZE;
    private static final int MAXIMUM_MASK_LENGTH = 64;
    private static final int REVEALED_SUFFIX_LENGTH = 4;
    private static final byte[] INDEX_DERIVATION_CONTEXT = "party-identifier-index".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] AAD_CONTEXT = "party-identifier-ciphertext-v1".getBytes(StandardCharsets.US_ASCII);
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final HexFormat LOWERCASE_HEX = HexFormat.of();

    private final CryptographicKeyMaterial keyMaterial;
    private final SecureRandom secureRandom;

    /**
     * Creates the adapter with eagerly validated keys and the JDK secure random
     * source.
     *
     * @param keyMaterial decoded in-memory cryptographic keys
     */
    @Inject
    public JcaIdentifierProtectionAdapter(CryptographicKeyMaterial keyMaterial) {
        this(keyMaterial, new SecureRandom());
    }

    JcaIdentifierProtectionAdapter(
            CryptographicKeyMaterial keyMaterial,
            SecureRandom secureRandom) {
        this.keyMaterial = Objects.requireNonNull(keyMaterial, "keyMaterial");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    public ProtectedIdentifierValue protect(IdentifierProtectionRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            int keyVersion = keyMaterial.currentEncryptionKeyVersion();
            String encryptedValue = encrypt(
                    request,
                    keyVersion,
                    keyMaterial.currentEncryptionKey());
            return new ProtectedIdentifierValue(
                    encryptedValue,
                    keyVersion,
                    normalizedValueHash(request.tenantId(), request.normalizedValue()),
                    mask(request.normalizedValue()),
                    request.normalizationVersion());
        } catch (GeneralSecurityException | CharacterCodingException | RuntimeException _) {
            throw protectionUnavailable();
        }
    }

    String decrypt(ProtectedIdentifierValue protectedValue, IdentifierProtectionRequest context) {
        Objects.requireNonNull(protectedValue, "protectedValue");
        Objects.requireNonNull(context, "context");
        byte[] plaintext = null;
        try {
            byte[][] envelope = decodeEnvelope(protectedValue.encryptedValue());
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    keyMaterial.encryptionKey(protectedValue.encryptionKeyVersion()),
                    new GCMParameterSpec(AUTHENTICATION_TAG_LENGTH_BITS, envelope[0]));
            cipher.updateAAD(aad(
                    context.tenantId(),
                    context.partyId(),
                    context.identifierId(),
                    context.identifierSchemeId(),
                    protectedValue.normalizationVersion(),
                    protectedValue.encryptionKeyVersion()));
            plaintext = cipher.doFinal(envelope[1]);
            return decodeUtf8(plaintext);
        } catch (GeneralSecurityException | CharacterCodingException | RuntimeException _) {
            throw new IllegalStateException(PROTECTION_FAILURE);
        } finally {
            if (plaintext != null) {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

    static String mask(String normalizedValue) {
        Objects.requireNonNull(normalizedValue, "normalizedValue");
        int length = normalizedValue.codePointCount(0, normalizedValue.length());
        int revealedLength = length > REVEALED_SUFFIX_LENGTH ? REVEALED_SUFFIX_LENGTH : 0;
        int hiddenLength = Math.min(length - revealedLength, MAXIMUM_MASK_LENGTH - revealedLength);
        if (revealedLength == 0) {
            return "*".repeat(hiddenLength);
        }
        int suffixStart = normalizedValue.offsetByCodePoints(0, length - revealedLength);
        return "*".repeat(hiddenLength) + normalizedValue.substring(suffixStart);
    }

    private String encrypt(
            IdentifierProtectionRequest request,
            int encryptionKeyVersion,
            SecretKey encryptionKey) throws GeneralSecurityException, CharacterCodingException {
        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        byte[] plaintext = encodeUtf8(request.completeValue());
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    encryptionKey,
                    new GCMParameterSpec(AUTHENTICATION_TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(aad(
                    request.tenantId(),
                    request.partyId(),
                    request.identifierId(),
                    request.identifierSchemeId(),
                    request.normalizationVersion(),
                    encryptionKeyVersion));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return ENVELOPE_VERSION
                    + "." + BASE64_URL_ENCODER.encodeToString(nonce)
                    + "." + BASE64_URL_ENCODER.encodeToString(ciphertext);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
        }
    }

    private String normalizedValueHash(TenantId tenantId, String normalizedValue)
            throws GeneralSecurityException, CharacterCodingException {
        byte[] tenantKey = deriveTenantIndexKey(tenantId);
        byte[] normalizedBytes = encodeUtf8(normalizedValue);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(tenantKey, HMAC_SHA_256));
            return LOWERCASE_HEX.formatHex(mac.doFinal(normalizedBytes));
        } finally {
            Arrays.fill(tenantKey, (byte) 0);
            Arrays.fill(normalizedBytes, (byte) 0);
        }
    }

    private byte[] deriveTenantIndexKey(TenantId tenantId) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_SHA_256);
        mac.init(keyMaterial.identifierIndexHmacKey());
        mac.update(INDEX_DERIVATION_CONTEXT);
        mac.update((byte) 0);
        return mac.doFinal(uuidBytes(tenantId.value()));
    }

    private static byte[] aad(
            TenantId tenantId,
            PartyId partyId,
            PartyIdentifierId identifierId,
            IdentifierSchemeId schemeId,
            IdentifierRuleVersion normalizationVersion,
            int encryptionKeyVersion) {
        return ByteBuffer.allocate(Integer.BYTES + AAD_CONTEXT.length + (4 * 2 * Long.BYTES) + (2 * Integer.BYTES))
                .putInt(AAD_CONTEXT.length)
                .put(AAD_CONTEXT)
                .put(uuidBytes(tenantId.value()))
                .put(uuidBytes(partyId.value()))
                .put(uuidBytes(identifierId.value()))
                .put(uuidBytes(schemeId.value()))
                .putInt(normalizationVersion.value())
                .putInt(encryptionKeyVersion)
                .array();
    }

    private static byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(2 * Long.BYTES)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    private static byte[] encodeUtf8(String value) throws CharacterCodingException {
        ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value));
        byte[] result = new byte[encoded.remaining()];
        encoded.get(result);
        return result;
    }

    private static String decodeUtf8(byte[] value) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(value))
                .toString();
    }

    private static byte[][] decodeEnvelope(String envelope) {
        String[] components = envelope.split("\\.", -1);
        if (components.length != 3
                || !ENVELOPE_VERSION.equals(components[0])
                || !isBase64Url(components[1])
                || !isBase64Url(components[2])) {
            throw new IllegalArgumentException(PROTECTION_FAILURE);
        }
        byte[] nonce = BASE64_URL_DECODER.decode(components[1]);
        byte[] ciphertext = BASE64_URL_DECODER.decode(components[2]);
        if (nonce.length != NONCE_LENGTH_BYTES || ciphertext.length <= AUTHENTICATION_TAG_LENGTH_BYTES) {
            throw new IllegalArgumentException(PROTECTION_FAILURE);
        }
        return new byte[][] { nonce, ciphertext };
    }

    private static boolean isBase64Url(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= 'A' && character <= 'Z')
                    || (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '-'
                    || character == '_')) {
                return false;
            }
        }
        return true;
    }

    private static ApplicationException protectionUnavailable() {
        return new ApplicationException(new ApplicationFailure.DependencyUnavailable(DEPENDENCY_NAME));
    }
}
