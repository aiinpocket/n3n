package com.aiinpocket.n3n.skill.controller;

import com.aiinpocket.n3n.skill.SkillResult;
import com.aiinpocket.n3n.skill.dto.*;
import com.aiinpocket.n3n.skill.service.SkillService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
@Tag(name = "Skills", description = "Skill management and execution")
public class SkillController {

    private final com.aiinpocket.n3n.auth.security.IpRateLimiter ipRateLimiter;

    private final SkillService skillService;

    /**
     * Get all accessible skills for current user.
     */
    @GetMapping
    public ResponseEntity<List<SkillDto>> getSkills(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(skillService.getAccessibleSkills(userId));
    }

    /**
     * Get all built-in skills.
     */
    @GetMapping("/builtin")
    public ResponseEntity<List<SkillDto>> getBuiltinSkills() {
        return ResponseEntity.ok(skillService.getBuiltinSkills());
    }

    /**
     * Get all available categories.
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(skillService.getCategories());
    }

    /**
     * Get accessible skills by category.
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<SkillDto>> getSkillsByCategory(
            @PathVariable @Size(max = 100) String category,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(skillService.getSkillsByCategory(category, userId));
    }

    /**
     * Get skill by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SkillDto> getSkill(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return skillService.getAccessibleSkill(id, userId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get skill by name with access control.
     */
    @GetMapping("/name/{name}")
    public ResponseEntity<SkillDto> getSkillByName(
            @PathVariable @Size(max = 255) String name,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return skillService.getSkillByName(name, userId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a custom skill.
     */
    @PostMapping
    public ResponseEntity<SkillDto> createSkill(
            @Valid @RequestBody CreateSkillRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(skillService.createSkill(request, userId));
    }

    /**
     * Update a custom skill.
     */
    @PutMapping("/{id}")
    public ResponseEntity<SkillDto> updateSkill(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSkillRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(skillService.updateSkill(id, request, userId));
    }

    /**
     * Delete a custom skill.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSkill(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        skillService.deleteSkill(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Execute a skill directly (for testing).
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<Map<String, Object>> executeSkill(
            @PathVariable UUID id,
            @Valid @RequestBody ExecuteSkillRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        // Rate limit: 10 skill executions per minute per user
        ipRateLimiter.checkAllowed("skill-execute", userId.toString(), 10, 60);
        SkillResult result = skillService.executeSkill(id, request.getInput(), userId);

        if (result.isSuccess()) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result.getData()
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", result.getErrorMessage(),
                "errorCode", result.getErrorCode() != null ? result.getErrorCode() : ""
            ));
        }
    }

    /**
     * Execute a skill by name (for testing).
     */
    @PostMapping("/name/{name}/execute")
    public ResponseEntity<Map<String, Object>> executeSkillByName(
            @PathVariable @Size(max = 255) String name,
            @Valid @RequestBody ExecuteSkillRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        // Rate limit: shares counter with executeSkill
        ipRateLimiter.checkAllowed("skill-execute", userId.toString(), 10, 60);
        SkillResult result = skillService.executeSkillByName(name, request.getInput(), userId);

        if (result.isSuccess()) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result.getData()
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", result.getErrorMessage(),
                "errorCode", result.getErrorCode() != null ? result.getErrorCode() : ""
            ));
        }
    }
}
