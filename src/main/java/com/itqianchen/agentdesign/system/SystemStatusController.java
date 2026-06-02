package com.itqianchen.agentdesign.system;

import com.itqianchen.agentdesign.storage.AppStorageInitializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    private static final String STATUS_UP = "UP";

    private final AppStorageInitializer storageInitializer;
    private final String version;

    public SystemStatusController(
            AppStorageInitializer storageInitializer,
            @Value("${app.version:${project.version:0.0.1-SNAPSHOT}}") String version
    ) {
        this.storageInitializer = storageInitializer;
        this.version = version;
    }

    @GetMapping("/status")
    public SystemStatusResponse status() {
        return new SystemStatusResponse(
                "CogniNote Agent",
                version,
                STATUS_UP,
                storageInitializer.appStorage().baseDir().toString()
        );
    }
}
