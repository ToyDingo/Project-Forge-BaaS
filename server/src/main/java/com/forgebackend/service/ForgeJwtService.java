package com.forgebackend.service;

import com.forgebackend.config.ForgeJwtProperties;
import com.forgebackend.exception.ForgeApiException;
import com.forgebackend.exception.ForgeErrorCode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Creates and verifies Forge-issued RS256 JWT access tokens.
 */
@Service
public class ForgeJwtService {

    public static final String CLAIM_GAME_ID = "game_id";
    public static final String CLAIM_PLATFORM = "platform";

    private final ForgeJwtProperties jwtProperties;
    private final ResourceLoader resourceLoader;

    private RSASSASigner signer;
    private RSASSAVerifier verifier;

    public ForgeJwtService(ForgeJwtProperties jwtProperties, ResourceLoader resourceLoader) {
        this.jwtProperties = jwtProperties;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Loads RSA PEM material from configured paths (fail fast if missing or invalid).
     */
    @PostConstruct
    void loadKeys() throws Exception {
        String privatePemPath = jwtProperties.privateKeyPemPath();
        String publicPemPath = jwtProperties.publicKeyPemPath();
        if (privatePemPath == null || privatePemPath.isBlank() || publicPemPath == null || publicPemPath.isBlank()) {
            throw new IllegalStateException(
                    "Set forge.jwt.private-key-pem-path and forge.jwt.public-key-pem-path (files or classpath:). See README.");
        }
        RSAPrivateKey privateKey = readPrivateKeyPem(readPem(privatePemPath));
        RSAPublicKey publicKey = readPublicKeyPem(readPem(publicPemPath));
        this.signer = new RSASSASigner(privateKey);
        this.verifier = new RSASSAVerifier(publicKey);
    }

    /**
     * Issues a signed access token for an authenticated player session.
     */
    public String createAccessToken(UUID playerId, UUID gameId, String platform) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(jwtProperties.accessTokenTtlSeconds());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(jwtProperties.issuer())
                .audience(jwtProperties.audience())
                .subject(playerId.toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .claim(CLAIM_GAME_ID, gameId.toString())
                .claim(CLAIM_PLATFORM, platform)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
        return jwt.serialize();
    }

    /**
     * Parses and verifies a bearer token; throws {@link ForgeApiException} if invalid or expired.
     */
    public ForgeAccessTokenClaims parseAndVerify(String bearerTokenWithoutPrefix) {
        try {
            SignedJWT jwt = SignedJWT.parse(bearerTokenWithoutPrefix);
            if (!jwt.verify(verifier)) {
                throw new ForgeApiException(ForgeErrorCode.FORGE_INVALID_TOKEN, "Invalid JWT signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime() != null && claims.getExpirationTime().before(new Date())) {
                throw new ForgeApiException(ForgeErrorCode.FORGE_INVALID_TOKEN, "JWT expired");
            }
            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) {
                throw new ForgeApiException(ForgeErrorCode.FORGE_INVALID_TOKEN, "JWT missing subject");
            }
            String gameIdStr = (String) claims.getClaim(CLAIM_GAME_ID);
            String platform = (String) claims.getClaim(CLAIM_PLATFORM);
            if (gameIdStr == null || platform == null) {
                throw new ForgeApiException(ForgeErrorCode.FORGE_INVALID_TOKEN, "JWT missing game or platform");
            }
            return new ForgeAccessTokenClaims(UUID.fromString(sub), UUID.fromString(gameIdStr), platform);
        } catch (ForgeApiException ex) {
            throw ex;
        } catch (ParseException | JOSEException e) {
            throw new ForgeApiException(ForgeErrorCode.FORGE_INVALID_TOKEN, "Invalid JWT");
        }
    }

    private String readPem(String location) throws Exception {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("JWT PEM resource not found: " + location);
        }
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    private static RSAPrivateKey readPrivateKeyPem(String pem) throws Exception {
        String stripped = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] pkcs8 = Base64.getDecoder().decode(stripped);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private static RSAPublicKey readPublicKeyPem(String pem) throws Exception {
        String stripped = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(stripped);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    /**
     * Claims extracted from a verified Forge access token.
     */
    public record ForgeAccessTokenClaims(UUID playerId, UUID gameId, String platform) {
    }
}
