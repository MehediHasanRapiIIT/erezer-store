package kn.org.deliverybackend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SwaggerLogger {

    private static final Logger logger = LoggerFactory.getLogger(SwaggerLogger.class);

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

    @PostConstruct
    public void logSwaggerEndpoint() {
        String swaggerUrl = "http://localhost:" + serverPort + contextPath + "swagger-ui/index.html";
        logger.info("Swagger UI is available at: {}", swaggerUrl);
    }
}
