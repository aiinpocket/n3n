package com.aiinpocket.n3n.auth.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(max = 100) String name
) {}
