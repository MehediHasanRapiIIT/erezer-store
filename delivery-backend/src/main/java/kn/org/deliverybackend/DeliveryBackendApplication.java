package kn.org.deliverybackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DeliveryBackendApplication {

    public static void main(String[] args) {
        // The shop trades in Bangladesh: every time the server reads, makes or
        // prints is Bangladesh time (UTC+6). The database still stores UTC:
        // Hibernate converts on the way in and out (hibernate.jdbc.time_zone).
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(
                System.getenv().getOrDefault("BUSINESS_ZONE", "Asia/Dhaka")));
        SpringApplication.run(DeliveryBackendApplication.class, args);
    }

}
