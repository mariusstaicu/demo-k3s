package com.example.demok3s;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
public class LivenessController {

    @GetMapping("/liveness")
    public ResponseEntity<Map<String, String>> get() {
        return ResponseEntity.ok(Collections.singletonMap("status", "UP and runningx`"));
    }
}
