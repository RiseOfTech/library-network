package io.github.riseoftech.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LibraryNetworkApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryNetworkApplication.class, args);
    }

}
