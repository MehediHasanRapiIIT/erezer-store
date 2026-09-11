package kn.org.deliverybackend.access;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Puts the permission check in front of every path the admin security chain
 * owns: all of /admin, plus product, category and banner changes under /api.
 */
@Configuration
@RequiredArgsConstructor
public class AccessControlWebConfig implements WebMvcConfigurer {

    private final PermissionInterceptor permissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/admin/**", "/api/products/**", "/api/categories/**", "/api/banners/**");
    }
}
