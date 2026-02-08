package com.aiinpocket.n3n.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserRolesRequest(
    @NotNull @Size(min = 1, max = 10) Set<String> roles
) {}
