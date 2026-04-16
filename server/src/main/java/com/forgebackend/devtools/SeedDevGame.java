package com.forgebackend.devtools;

import com.forgebackend.security.ForgeApiKeyHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.UUID;

/**
 * Inserts a dev {@code games} row with properly hashed API key.
 * <p>
 * Usage: {@code ./gradlew seedDevGame --args="my-raw-key"}
 */
public final class SeedDevGame {

    public static void main(String[] args) {
        String rawKey = args.length > 0 ? args[0] : "dev-api-key";
        String gameName = args.length > 1 ? args[1] : "Dev Game";
        long steamAppId = 480L;

        String url = envOrDefault("FORGE_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/app");
        String user = envOrDefault("FORGE_DATASOURCE_USERNAME", "app");
        String pass = envOrDefault("FORGE_DATASOURCE_PASSWORD", "app");

        String lookupHash = ForgeApiKeyHasher.sha256HexLowercase(rawKey);
        String bcryptHash = new BCryptPasswordEncoder().encode(rawKey);

        String sql =
                "INSERT INTO games (id, name, api_key_hash, api_key_lookup_hash, steam_app_id, steam_web_api_key, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, now())";

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, gameName);
            ps.setString(3, bcryptHash);
            ps.setString(4, lookupHash);
            ps.setLong(5, steamAppId);
            ps.setString(6, "dev-steam-web-api-key");
            int rows = ps.executeUpdate();
            System.out.println("Inserted " + rows + " row(s). Raw API key: " + rawKey);
        } catch (Exception e) {
            System.err.println("Failed to seed game: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String envOrDefault(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
