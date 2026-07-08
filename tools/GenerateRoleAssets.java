import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** One-off generator: javac tools/GenerateRoleAssets.java && java -cp tools GenerateRoleAssets */
public class GenerateRoleAssets {

    private static final String ADMIN =
        "NLOON-ADMIN-OFFLINE-2026-k8Qm3xZp9wRv7nLc4jHd6fGa2bYe5tUi1oPs0QrWxYzAbCdEfGhIjKlMnOp";
    private static final String TEACHER =
        "NLOON-TEACHER-OFFLINE-2026-m4nLc7jHd9fGa6bYe3tUi8oPs2kQm1xZp5wRv0QxYzAbCdEfGhIjKlMnOpQr";

    public static void main(String[] args) throws Exception {
        Path assets = Path.of("app", "src", "main", "assets");
        Files.createDirectories(assets);

        String hashJson = "{\"admin\":\"" + sha256(ADMIN) + "\",\"teacher\":\"" + sha256(TEACHER) + "\"}";
        String qrJson = "{\"admin\":\"" + qr("ADMIN", ADMIN) + "\",\"teacher\":\"" + qr("TEACHER", TEACHER) + "\"}";

        Files.write(assets.resolve("role_keys.enc"), encrypt(hashJson, "com.neon.android.offline.rolekeys.v1"));
        Files.write(assets.resolve("role_qr.enc"), encrypt(qrJson, "com.neon.android.offline.roleqr.v1"));
        System.out.println("Wrote role_keys.enc and role_qr.enc");
    }

    private static String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(input.trim().getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String qr(String role, String key) throws Exception {
        return "nlogn://role/v1/" + role + "#" + URLEncoder.encode(key.trim(), StandardCharsets.UTF_8);
    }

    private static byte[] encrypt(String json, String label) throws Exception {
        byte[] plain = json.getBytes(StandardCharsets.UTF_8);
        byte[] key = MessageDigest.getInstance("SHA-256").digest(label.getBytes(StandardCharsets.UTF_8));
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plain);
        byte[] out = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
        return out;
    }
}
