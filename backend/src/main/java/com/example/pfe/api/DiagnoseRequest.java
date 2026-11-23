package com.example.pfe.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DiagnoseRequest(
        @NotBlank(message = "Paste a pipeline log to analyse.")
        @Size(max = 100_000, message = "Log is too large (max 100,000 characters). Paste the failing step only.")
        String log) {}
