package group.moniepoint.eventsnestserver.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;

/**
 * Uploads to AWS S3. Selected by {@code app.storage.type=s3}. The bucket is
 * configured to allow public read (or fronted by CloudFront) so the returned
 * URL can be rendered by the frontend without signed-URL gymnastics.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3FileStorageService implements FileStorageService {

    private final String bucket;
    private final Region region;
    private final String publicBaseUrl;
    private S3Client s3;

    public S3FileStorageService(
            @Value("${app.storage.s3.bucket}") String bucket,
            @Value("${app.storage.s3.region:eu-west-1}") String region,
            @Value("${app.storage.s3.public-base-url:}") String publicBaseUrlOverride) {
        this.bucket = bucket;
        this.region = Region.of(region);
        // If a CloudFront / custom domain is fronting the bucket, prefer that;
        // otherwise fall back to the default virtual-hosted style S3 URL.
        this.publicBaseUrl = publicBaseUrlOverride != null && !publicBaseUrlOverride.isBlank()
                ? trimTrailingSlash(publicBaseUrlOverride)
                : "https://" + bucket + ".s3." + region + ".amazonaws.com";
    }

    @PostConstruct
    void init() {
        this.s3 = S3Client.builder().region(region).build();
        log.info("S3FileStorageService active — bucket={}, region={}, publicBaseUrl={}",
                bucket, region, publicBaseUrl);
    }

    @PreDestroy
    void close() {
        if (s3 != null) s3.close();
    }

    @Override
    public String store(String path, String contentType, long size, InputStream data) throws IOException {
        try {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(path)
                            .contentType(contentType)
                            .contentLength(size)
                            .build(),
                    RequestBody.fromInputStream(data, size));
        } catch (RuntimeException e) {
            throw new IOException("S3 upload failed for key " + path, e);
        }
        return publicBaseUrl + "/" + path;
    }

    @Override
    public void delete(String url) throws IOException {
        if (url == null || !url.startsWith(publicBaseUrl + "/")) return;
        String key = url.substring(publicBaseUrl.length() + 1);
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException ignored) {
            // already gone
        } catch (RuntimeException e) {
            throw new IOException("S3 delete failed for key " + key, e);
        }
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
