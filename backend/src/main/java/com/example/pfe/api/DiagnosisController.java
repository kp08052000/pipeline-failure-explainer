package com.example.pfe.api;

import com.example.pfe.model.Diagnosis;
import com.example.pfe.service.DiagnosisService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DiagnosisController {

    private final DiagnosisService service;

    public DiagnosisController(DiagnosisService service) {
        this.service = service;
    }

    @PostMapping("/diagnose")
    public Diagnosis diagnose(@Valid @RequestBody DiagnoseRequest request) {
        return service.diagnose(request.log());
    }

    @GetMapping("/status")
    public Map<String, String> status() {
        return Map.of("provider", service.providerName());
    }
}
