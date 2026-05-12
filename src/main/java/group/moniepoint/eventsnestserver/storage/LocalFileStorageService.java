package group.moniepoint.eventsnestserver.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Saves uploads to a local directory and serves them via the static resource
 * handler configured in {@link StorageConfig}. Intended for local dev only —
 * production deployments should set {@code app.storage.type=s3}.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private final Path uploadsRoot;
    private final String publicBaseUrl;

    public LocalFileStorageService(
            @Value("${app.storage.local.root:./uploads}") String root,
            @Value("${app.storage.local.public-base-url:http://localhost:8080/uploads}") String publicBaseUrl) {
        this.uploadsRoot = Paths.get(root).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
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
