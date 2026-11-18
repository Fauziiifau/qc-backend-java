package com.gmp.defect_system.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Membaca bukti foto dari Supabase Storage (bucket PUBLIC saat ini).
 * Sumber nama objek hanya dari kolom photo_url di database, bukan URL dari user.
 * Tidak meng-upload foto dan tidak mengubah permission/RLS Storage.
 */
@Service
public class DefectPhotoStorageService {
    private static final Logger log = LoggerFactory.getLogger(DefectPhotoStorageService.class);
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final String supabaseUrl;
    private final String bucket;

    public DefectPhotoStorageService(
            @Value("${SUPABASE_URL:}") String supabaseUrl,
            @Value("${SUPABASE_PHOTO_BUCKET:defect-photos}") String bucket) {
        this.supabaseUrl = supabaseUrl == null ? "" : supabaseUrl.trim().replaceAll("/+$", "");
        this.bucket = bucket == null ? "" : bucket.trim();
    }

    public String storageReference(String objectName) {
        return objectName == null || objectName.isBlank() ? "" : bucket + "/" + objectName.trim();
    }

    public Optional<byte[]> fetchImage(String objectName) {
        if (supabaseUrl.isEmpty() || !isValidBucket(bucket)) {
            log.warn("SUPABASE_URL / SUPABASE_PHOTO_BUCKET belum dikonfigurasi; bukti foto tidak disisipkan.");
            return Optional.empty();
        }
        if (objectName == null || objectName.isBlank() || objectName.length() > 512) {
            return Optional.empty();
        }

        // Kolom ng_data.photo_url di project ini berisi nama/path objek, BUKAN URL.
        // Validasi untuk mencegah path traversal dan URL/host yang diberikan dari data.
        String path = objectName.trim();
        String[] segments = path.split("/", -1);
        if (path.startsWith("/") || path.contains("\\") || path.contains("?") || path.contains("#")
                || Arrays.stream(segments).anyMatch(s -> s.isBlank() || s.equals(".") || s.equals(".."))) {
            log.warn("Path bukti foto tidak valid dan diabaikan.");
            return Optional.empty();
        }

        try {
            URI base = URI.create(supabaseUrl);
            String host = base.getHost();
            // Project ini menggunakan hosted Supabase; jangan izinkan alamat eksternal/internal acak.
            if (!"https".equalsIgnoreCase(base.getScheme()) || host == null
                    || !host.endsWith(".supabase.co") || base.getPort() != -1
                    || !(base.getPath() == null || base.getPath().isBlank() || base.getPath().equals("/"))) {
                log.warn("SUPABASE_URL harus berupa URL HTTPS project Supabase, misalnya https://PROJECT.supabase.co");
                return Optional.empty();
            }
            String objectPath = Arrays.stream(segments)
                    .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                    .reduce((a, b) -> a + "/" + b).orElse("");
            URI uri = URI.create(supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + objectPath);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "image/jpeg,image/png")
                    .GET().build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    log.warn("Foto pada Supabase Storage tidak tersedia (HTTP {}).", response.statusCode());
                    return Optional.empty();
                }
                if (response.headers().firstValueAsLong("content-length").orElse(0L) > MAX_IMAGE_BYTES) {
                    log.warn("Ukuran file foto melebihi 4 MB; dilewati.");
                    return Optional.empty();
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (bytes.size() + count > MAX_IMAGE_BYTES) {
                        log.warn("Ukuran file foto melebihi 4 MB saat dibaca; dilewati.");
                        return Optional.empty();
                    }
                    bytes.write(buffer, 0, count);
                }
                byte[] image = bytes.toByteArray();
                if (!isJpeg(image) && !isPng(image)) {
                    log.warn("File Storage bukan JPG/PNG yang valid; dilewati.");
                    return Optional.empty();
                }
                return Optional.of(image);
            }
        } catch (IOException e) {
            log.warn("Gagal mengunduh foto dari Supabase Storage: {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Unduhan foto terinterupsi.");
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            log.warn("Alamat foto tidak valid: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public static int poiPictureType(byte[] bytes) {
        return isPng(bytes) ? org.apache.poi.ss.usermodel.Workbook.PICTURE_TYPE_PNG
                : org.apache.poi.ss.usermodel.Workbook.PICTURE_TYPE_JPEG;
    }

    private static boolean isValidBucket(String bucket) {
        return bucket.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,99}");
    }

    private static boolean isJpeg(byte[] b) {
        return b != null && b.length >= 3 && (b[0] & 0xff) == 0xff
                && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff;
    }

    private static boolean isPng(byte[] b) {
        byte[] sig = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (b == null || b.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) if (b[i] != sig[i]) return false;
        return true;
    }
}
