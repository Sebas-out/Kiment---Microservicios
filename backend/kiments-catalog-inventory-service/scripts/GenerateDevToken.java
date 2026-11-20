import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public class GenerateDevToken {
    public static void main(String[] args) throws Exception {
        String userHome = System.getProperty("user.home");
        Path keyPath = Path.of(System.getenv("LOCALAPPDATA"), "Kiments", "auth-keys", "private.pem");
        if (!Files.exists(keyPath)) {
            System.err.println("Key file not found: " + keyPath);
            System.exit(1);
        }

        String pem = Files.readString(keyPath);
        String b64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(b64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPrivateKey privateKey = (RSAPrivateKey) kf.generatePrivate(spec);

        long now = Instant.now().getEpochSecond();
        long exp = now + 86400; // 24 hours

        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"kiments-rsa-1\"}";
        String payload = "{"
                + "\"sub\":\"ac4e9199-01ba-4ee0-98d6-fa7e10001fe4\","
                + "\"iss\":\"http://localhost:8080\","
                + "\"aud\":\"kiments-api\","
                + "\"iat\":" + now + ","
                + "\"nbf\":" + now + ","
                + "\"exp\":" + exp + ","
                + "\"jti\":\"" + UUID.randomUUID() + "\","
                + "\"role\":\"ADMIN\","
                + "\"permissions\":[\"CATEGORY_READ\",\"CATEGORY_WRITE\",\"PRODUCT_READ\",\"PRODUCT_WRITE\",\"STOCK_READ\",\"STOCK_ADJUST\",\"STOCK_TRANSFER\",\"MOVEMENT_READ\",\"REPORT_READ\"]"
                + "}";

        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String h64 = enc.encodeToString(header.getBytes("UTF-8"));
        String p64 = enc.encodeToString(payload.getBytes("UTF-8"));
        String signingInput = h64 + "." + p64;

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(signingInput.getBytes("UTF-8"));
        String sig64 = enc.encodeToString(signer.sign());

        String jwt = signingInput + "." + sig64;
        System.out.println("=== GENERATED DEV JWT TOKEN ===");
        System.out.println(jwt);

        HttpClient client = HttpClient.newHttpClient();

        System.out.println("\n=== TESTING /api/v1/stock-movements?page=0&size=10 ===");
        HttpRequest req1 = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8082/api/v1/stock-movements?page=0&size=10"))
                .header("Authorization", "Bearer " + jwt)
                .GET()
                .build();
        HttpResponse<String> resp1 = client.send(req1, HttpResponse.BodyHandlers.ofString());
        System.out.println("Status 1: " + resp1.statusCode());
        System.out.println("Body 1: " + resp1.body());

        System.out.println("\n=== TESTING /api/v1/stock-transfers?page=0&size=10 ===");
        HttpRequest req2 = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8082/api/v1/stock-transfers?page=0&size=10"))
                .header("Authorization", "Bearer " + jwt)
                .GET()
                .build();
        HttpResponse<String> resp2 = client.send(req2, HttpResponse.BodyHandlers.ofString());
        System.out.println("Status 2: " + resp2.statusCode());
        System.out.println("Body 2: " + resp2.body());

        System.out.println("\n=== TESTING /api/v1/reports/inventory/summary ===");
        HttpRequest req3 = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8082/api/v1/reports/inventory/summary"))
                .header("Authorization", "Bearer " + jwt)
                .GET()
                .build();
        HttpResponse<String> resp3 = client.send(req3, HttpResponse.BodyHandlers.ofString());
        System.out.println("Status 3: " + resp3.statusCode());
        System.out.println("Body 3: " + resp3.body());
    }
}
