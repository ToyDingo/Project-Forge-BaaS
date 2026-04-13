import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/**
 * One-off: {@code java scripts/GenerateJwtKeys.java} writes dev PEMs under src/main/resources/keys/.
 */
public class GenerateJwtKeys {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAPrivateKey priv = (RSAPrivateKey) pair.getPrivate();
        RSAPublicKey pub = (RSAPublicKey) pair.getPublic();

        Path dir = Path.of("src/main/resources/keys");
        Files.createDirectories(dir);
        writePrivatePem(dir.resolve("dev-private.pem"), priv.getEncoded());
        writePublicPem(dir.resolve("dev-public.pem"), pub.getEncoded());
        System.out.println("Wrote " + dir.toAbsolutePath().normalize());
    }

    private static void writePrivatePem(Path path, byte[] pkcs8) throws IOException {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(pkcs8);
        String pem = "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
        Files.writeString(path, pem);
    }

    private static void writePublicPem(Path path, byte[] spki) throws IOException {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(spki);
        String pem = "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----\n";
        Files.writeString(path, pem);
    }
}
