package group.moniepoint.eventsnestserver.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * When LocalFileStorageService is active, expose its uploads directory under
 * {@code /uploads/**} so the frontend can fetch saved images directly. The S3
 * profile doesn't need this — S3 URLs are absolute.
 */
@Configuration
@ConditionalOnBean(LocalFileStorageService.class)
public class StorageConfig implements WebMvcConfigurer {

    private final LocalFileStorageService localStorage;

    public StorageConfig(LocalFileStorageService localStorage) {
        this.localStorage = localStorage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + localStorage.getUploadsRoot().toString() + "/");
    }
}
