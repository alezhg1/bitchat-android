import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates printable QR PNGs from app/src/main/assets/role_qr.enc
 *
 * Usage (from repo root, after ./gradlew :app:dependencies resolved):
 *   javac -cp "$HOME/.gradle/caches/modules-2/files-2.1/com.google.zxing/core/3.5.4/*/core-3.5.4.jar:$HOME/.gradle/caches/modules-2/files-2.1/com.google.zxing/javase/3.5.4/*/javase-3.5.4.jar" tools/GenerateRoleQrImages.java
 *   java -cp tools:...jars... GenerateRoleQrImages
 *
 * Or simply run: ./gradlew printRoleQr
 */
public class GenerateRoleQrImages {

    private static final Pattern JSON_FIELD = Pattern.compile("\"(admin|teacher)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    public static void main(String[] args) throws Exception {
        Path enc = Path.of("app", "src", "main", "assets", "role_qr.enc");
        if (!Files.exists(enc)) {
            System.err.println("Missing " + enc + " — run tools/GenerateRoleAssets.java first.");
            System.exit(1);
        }

        String json = decrypt(Files.readAllBytes(enc), "com.neon.android.offline.roleqr.v1");
        Path outDir = Path.of("docs", "qr");
        Files.createDirectories(outDir);

        Matcher m = JSON_FIELD.matcher(json);
        int count = 0;
        while (m.find()) {
            String name = m.group(1);
            String payload = unescapeJson(m.group(2));
            Path png = outDir.resolve("role_" + name + ".png");
            writeQr(payload, png, 512);
            Files.writeString(outDir.resolve("role_" + name + ".txt"), payload, StandardCharsets.UTF_8);
            System.out.println("Wrote " + png.toAbsolutePath());
            count++;
        }

        if (count == 0) {
            System.err.println("No QR payloads found in role_qr.enc");
            System.exit(1);
        }
        System.out.println("Open docs/qr/ — scan role_admin.png for admin login, role_teacher.png for teacher.");
    }

    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static void writeQr(String data, Path out, int size) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size);
        MatrixToImageWriter.writeToPath(matrix, "PNG", out);
    }

    private static String decrypt(byte[] payload, String label) throws Exception {
        byte[] iv = payload.clone();
        iv = java.util.Arrays.copyOfRange(payload, 0, 12);
        byte[] ciphertext = java.util.Arrays.copyOfRange(payload, 12, payload.length);
        byte[] key = MessageDigest.getInstance("SHA-256").digest(label.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }
}
