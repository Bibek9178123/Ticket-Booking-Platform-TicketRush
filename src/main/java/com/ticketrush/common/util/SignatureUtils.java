package com.ticketrush.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

public final class SignatureUtils {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long MAX_ALLOWED_AGE_SECONDS = 300; // 5 minutes replay window

    private SignatureUtils() {
    }

    /**
     * Computes HMAC-SHA256 over input string data using the given secret key.
     */
    public static String computeHmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256", e);
        }
    }

    /**
     * Verifies Stripe-style signed webhook headers: "t=<unix_timestamp>,v1=<hex_hash>".
     * Signature is calculated over: t + "." + rawPayload.
     * Uses constant-time comparison to prevent timing attacks.
     */
    public static boolean verifyWebhookSignature(String secret, String rawPayload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }

        String timestampStr = null;
        String signatureHex = null;

        String[] parts = signatureHeader.split(",");
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                if ("t".equalsIgnoreCase(kv[0])) {
                    timestampStr = kv[1];
                } else if ("v1".equalsIgnoreCase(kv[0])) {
                    signatureHex = kv[1];
                }
            }
        }

        if (timestampStr == null || signatureHex == null) {
            return false;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            return false;
        }

        // Replay Attack Defense: Reject requests older than 5 minutes
        long currentUnix = Instant.now().getEpochSecond();
        if (Math.abs(currentUnix - timestamp) > MAX_ALLOWED_AGE_SECONDS) {
            return false;
        }

        String signedPayload = timestampStr + "." + rawPayload;
        String expectedHex = computeHmacHex(secret, signedPayload);

        // Constant-time equality comparison
        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                signatureHex.getBytes(StandardCharsets.UTF_8)
        );
    }
}
