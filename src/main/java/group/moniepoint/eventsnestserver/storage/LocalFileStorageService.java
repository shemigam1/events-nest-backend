package group.moniepoint.eventsnestserver.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saves uploads to a local directory and serves them via the static resource
 * handler configured in {@link StorageConfig}. Intended for local dev only —
 * production deployments should set {@code app.storage.type=s3}.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    /** token → storage key. Consumed on first PUT; auto-expires on server restart (local dev only). */
    final Map<String, String> pendingUploads = new ConcurrentHashMap<>();

    private final Path uploadsRoot;
    private final String publicBaseUrl;
    private final String appBaseUrl;

    public LocalFileStorageService(
            @Value("${app.storage.local.root:./uploads}") String root,
            @Value("${app.storage.local.public-base-url:http://localhost:8080/uploads}") String publicBaseUrl,
            @Value("${app.storage.local.app-base-url:http://localhost:8080}") String appBaseUrl) {
        this.uploadsRoot = Paths.get(root).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        this.appBaseUrl = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
        try {
            Files.createDirectories(this.uploadsRoot);
        } catch (IOException e) {
            throw new IllegalStateException("failed to create uploads dir: " + this.uploadsRoot, e);
        }
        log.info("LocalFileStorageService active — root={}, publicBaseUrl={}", this.uploadsRoot, this.publicBaseUrl);
    }

    @Override
    public String store(String path, String contentType, long size, InputStream data) throws IOException {
        Path target = uploadsRoot.resolve(path).normalize();
        if (!target.startsWith(uploadsRoot)) {
            throw new IOException("refusing to write outside uploads root: " + path);
        }
        Files.createDirectories(target.getParent());
        Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
        return publicBaseUrl + "/" + path;
    }

    /**
     * For local dev: mint a one-time upload token and return a URL pointing at
     * the local upload endpoint. The frontend PUTs the raw file bytes there.
     */
    @Override
    public PresignResult presignPut(String key, String contentType) {
        String token = UUID.randomUUID().toString();
        pendingUploads.put(token, key);
        String uploadUrl = appBaseUrl + "/api/v1/storage/local-upload/" + token;
        String publicUrl = publicBaseUrl + "/" + key;
        return new PresignResult(uploadUrl, publicUrl);
    }

    /**
     * Called by {@link LocalUploadController} after it receives the raw PUT body.
     * Stores the bytes at the path encoded in the token.
     *
     * @return the public URL of the stored file, or {@code null} if token is unknown.
     */
    public String consumeUpload(String token, String contentType, long size, InputStream data) throws IOException {
        String key = pendingUploads.remove(token);
        if (key == null) return null;
        store(key, contentType, size, data);
        return publicBaseUrl + "/" + key;
    }

    @Override
    public void delete(String url) throws IOException {
        if (url == null || !url.startsWith(publicBaseUrl + "/")) {
            return; // unknown URL — swallow
        }
        String relative = url.substring(publicBaseUrl.length() + 1);
        Path target = uploadsRoot.resolve(relative).normalize();
        if (!target.startsWith(uploadsRoot)) {
            return;
        }
        Files.deleteIfExists(target);
    }

    /** For tests / static resource handler. */
    public Path getUploadsRoot() {
        return uploadsRoot;
    }
}
