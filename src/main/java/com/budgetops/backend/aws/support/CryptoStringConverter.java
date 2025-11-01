package com.budgetops.backend.aws.support;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Converter
public class CryptoStringConverter implements AttributeConverter<String, String> {
    private static final String ALG = "AES";
    private static final String TRANS = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;

    private static byte[] key() {
        return CryptoKeyProvider.getKeyBytes();
    }

    @Override
    public String convertToDatabaseColumn(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance(TRANS);
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), ALG), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes());
            return Base64.getEncoder().encodeToString(iv) + "|" + Base64.getEncoder().encodeToString(ct);
        } catch (Exception e) {
            throw new IllegalStateException("Encrypt failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String db) {
        if (db == null) return null;
        try {
            String[] parts = db.split("\\|");
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ct = Base64.getDecoder().decode(parts[1]);
            Cipher c = Cipher.getInstance(TRANS);
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), ALG), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(c.doFinal(ct));
        } catch (Exception e) {
            throw new IllegalStateException("Decrypt failed", e);
        }
    }
}


