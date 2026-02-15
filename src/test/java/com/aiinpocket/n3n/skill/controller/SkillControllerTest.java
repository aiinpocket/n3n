package com.aiinpocket.n3n.skill.controller;

import com.aiinpocket.n3n.skill.SkillResult;
import com.aiinpocket.n3n.skill.dto.*;
import com.aiinpocket.n3n.skill.service.SkillService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillControllerTest {

    @Mock
    private SkillService skillService;

    @InjectMocks
    private SkillController skillController;

    // ===== Helpers =====

    private UserDetails testUser() {
        return User.withUsername(UUID.randomUUID().toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private UserDetails testUserWithId(UUID userId) {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private SkillDto sampleSkillDto() {
        return SkillDto.builder()
                .id(UUID.randomUUID())
                .name("parse_json")
                .displayName("Parse JSON")
                .description("Parses JSON input")
                .category("data")
                .icon("code")
                .isBuiltin(true)
                .isEnabled(true)
                .implementationType("builtin")
                .inputSchema(Map.of("type", "object"))
                .outputSchema(Map.of("type", "object"))
                .visibility("public")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private SkillDto customSkillDto(UUID ownerId) {
        return SkillDto.builder()
                .id(UUID.randomUUID())
                .name("my_custom_skill")
                .displayName("My Custom Skill")
                .description("A custom skill")
                .category("custom")
                .icon("star")
                .isBuiltin(false)
                .isEnabled(true)
                .implementationType("flow")
                .inputSchema(Map.of("type", "object"))
                .outputSchema(Map.of("type", "object"))
                .visibility("private")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ===== getSkills (GET /api/skills) =====

    @Test
    void getSkills_returnsAccessibleSkills() {
        var user = testUser();
        var skill1 = sampleSkillDto();
        var skill2 = customSkillDto(null);
        when(skillService.getAccessibleSkills(any(UUID.class))).thenReturn(List.of(skill1, skill2));

        var result = skillController.getSkills(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody().get(0).getName()).isEqualTo("parse_json");
        assertThat(result.getBody().get(1).getName()).isEqualTo("my_custom_skill");
        verify(skillService).getAccessibleSkills(any(UUID.class));
    }

    @Test
    void getSkills_emptyList_returnsOk() {
        var user = testUser();
        when(skillService.getAccessibleSkills(any(UUID.class))).thenReturn(List.of());

        var result = skillController.getSkills(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void getSkills_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        when(skillService.getAccessibleSkills(eq(userId))).thenReturn(List.of());

        skillController.getSkills(user);

        verify(skillService).getAccessibleSkills(eq(userId));
    }

    // ===== getBuiltinSkills (GET /api/skills/builtin) =====

    @Test
    void getBuiltinSkills_returnsList() {
        var skill1 = sampleSkillDto();
        var skill2 = SkillDto.builder()
                .id(UUID.randomUUID())
                .name("fetch_url")
                .displayName("Fetch URL")
                .category("web")
                .isBuiltin(true)
                .isEnabled(true)
                .build();
        when(skillService.getBuiltinSkills()).thenReturn(List.of(skill1, skill2));

        var result = skillController.getBuiltinSkills();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody()).extracting(SkillDto::getIsBuiltin)
                .containsOnly(true);
    }

    @Test
    void getBuiltinSkills_empty_returnsEmptyList() {
        when(skillService.getBuiltinSkills()).thenReturn(List.of());

        var result = skillController.getBuiltinSkills();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    // ===== getCategories (GET /api/skills/categories) =====

    @Test
    void getCategories_returnsCategoryList() {
        when(skillService.getCategories()).thenReturn(List.of("data", "web", "notify", "http"));

        var result = skillController.getCategories();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(4);
        assertThat(result.getBody()).containsExactly("data", "web", "notify", "http");
    }

    @Test
    void getCategories_empty_returnsEmptyList() {
        when(skillService.getCategories()).thenReturn(List.of());

        var result = skillController.getCategories();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    // ===== getSkillsByCategory (GET /api/skills/category/{category}) =====

    @Test
    void getSkillsByCategory_returnsMatchingSkills() {
        var skill = sampleSkillDto();
        when(skillService.getSkillsByCategory("data")).thenReturn(List.of(skill));

        var result = skillController.getSkillsByCategory("data");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getCategory()).isEqualTo("data");
    }

    @Test
    void getSkillsByCategory_noMatch_returnsEmptyList() {
        when(skillService.getSkillsByCategory("nonexistent")).thenReturn(List.of());

        var result = skillController.getSkillsByCategory("nonexistent");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void getSkillsByCategory_multipleMatches_returnsAll() {
        var skill1 = SkillDto.builder().id(UUID.randomUUID()).name("skill1").category("web").build();
        var skill2 = SkillDto.builder().id(UUID.randomUUID()).name("skill2").category("web").build();
        when(skillService.getSkillsByCategory("web")).thenReturn(List.of(skill1, skill2));

        var result = skillController.getSkillsByCategory("web");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody()).extracting(SkillDto::getName)
                .containsExactly("skill1", "skill2");
    }

    // ===== getSkill (GET /api/skills/{id}) =====

    @Test
    void getSkill_found_returnsOk() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        var skill = SkillDto.builder()
                .id(skillId)
                .name("my_skill")
                .displayName("My Skill")
                .category("custom")
                .isBuiltin(false)
                .build();
        when(skillService.getAccessibleSkill(eq(skillId), any(UUID.class))).thenReturn(Optional.of(skill));

        var result = skillController.getSkill(skillId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getId()).isEqualTo(skillId);
        assertThat(result.getBody().getName()).isEqualTo("my_skill");
    }

    @Test
    void getSkill_notFound_returnsNotFound() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        when(skillService.getAccessibleSkill(eq(skillId), any(UUID.class))).thenReturn(Optional.empty());

        var result = skillController.getSkill(skillId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNull();
    }

    @Test
    void getSkill_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var skillId = UUID.randomUUID();
        when(skillService.getAccessibleSkill(eq(skillId), eq(userId))).thenReturn(Optional.empty());

        skillController.getSkill(skillId, user);

        verify(skillService).getAccessibleSkill(eq(skillId), eq(userId));
    }

    // ===== getSkillByName (GET /api/skills/name/{name}) =====

    @Test
    void getSkillByName_found_returnsOk() {
        var skill = SkillDto.builder()
                .id(UUID.randomUUID())
                .name("parse_json")
                .displayName("Parse JSON")
                .category("data")
                .build();
        when(skillService.getSkillByName("parse_json")).thenReturn(Optional.of(skill));

        var result = skillController.getSkillByName("parse_json");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("parse_json");
    }

    @Test
    void getSkillByName_notFound_returnsNotFound() {
        when(skillService.getSkillByName("nonexistent_skill")).thenReturn(Optional.empty());

        var result = skillController.getSkillByName("nonexistent_skill");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNull();
    }

    // ===== createSkill (POST /api/skills) =====

    @Test
    void createSkill_success_returnsCreated() {
        var user = testUser();
        var request = new CreateSkillRequest();
        request.setName("new_skill");
        request.setDisplayName("New Skill");
        request.setDescription("A new custom skill");
        request.setCategory("custom");
        request.setIcon("star");
        request.setImplementationType("flow");
        request.setInputSchema(Map.of("type", "object"));
        request.setOutputSchema(Map.of("type", "object"));
        request.setVisibility("private");

        var response = SkillDto.builder()
                .id(UUID.randomUUID())
                .name("new_skill")
                .displayName("New Skill")
                .description("A new custom skill")
                .category("custom")
                .icon("star")
                .isBuiltin(false)
                .isEnabled(true)
                .implementationType("flow")
                .inputSchema(Map.of("type", "object"))
                .outputSchema(Map.of("type", "object"))
                .visibility("private")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(skillService.createSkill(any(CreateSkillRequest.class), any(UUID.class))).thenReturn(response);

        var result = skillController.createSkill(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("new_skill");
        assertThat(result.getBody().getDisplayName()).isEqualTo("New Skill");
        assertThat(result.getBody().getIsBuiltin()).isFalse();
        assertThat(result.getBody().getIsEnabled()).isTrue();
        verify(skillService).createSkill(any(CreateSkillRequest.class), any(UUID.class));
    }

    @Test
    void createSkill_duplicateName_throwsException() {
        var user = testUser();
        var request = new CreateSkillRequest();
        request.setName("existing_skill");
        request.setDisplayName("Existing Skill");
        request.setCategory("custom");
        request.setImplementationType("flow");
        request.setInputSchema(Map.of("type", "object"));

        when(skillService.createSkill(any(CreateSkillRequest.class), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("Skill with name already exists: existing_skill"));

        assertThatThrownBy(() -> skillController.createSkill(request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createSkill_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var request = new CreateSkillRequest();
        request.setName("userid_test_skill");
        request.setDisplayName("User ID Test");
        request.setCategory("test");
        request.setImplementationType("flow");
        request.setInputSchema(Map.of("type", "object"));

        var response = SkillDto.builder()
                .id(UUID.randomUUID())
                .name("userid_test_skill")
                .build();
        when(skillService.createSkill(any(CreateSkillRequest.class), eq(userId))).thenReturn(response);

        skillController.createSkill(request, user);

        verify(skillService).createSkill(any(CreateSkillRequest.class), eq(userId));
    }

    // ===== updateSkill (PUT /api/skills/{id}) =====

    @Test
    void updateSkill_success_returnsOk() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        var request = new UpdateSkillRequest();
        request.setDisplayName("Updated Skill Name");
        request.setDescription("Updated description");
        request.setCategory("updated-category");

        var response = SkillDto.builder()
                .id(skillId)
                .name("my_skill")
                .displayName("Updated Skill Name")
                .description("Updated description")
                .category("updated-category")
                .isBuiltin(false)
                .isEnabled(true)
                .build();
        when(skillService.updateSkill(eq(skillId), any(UpdateSkillRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = skillController.updateSkill(skillId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getDisplayName()).isEqualTo("Updated Skill Name");
        assertThat(result.getBody().getDescription()).isEqualTo("Updated description");
        assertThat(result.getBody().getCategory()).isEqualTo("updated-category");
    }

    @Test
    void updateSkill_notFound_throwsException() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        var request = new UpdateSkillRequest();
        request.setDescription("Updated");

        when(skillService.updateSkill(eq(skillId), any(UpdateSkillRequest.class), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("Skill not found: " + skillId));

        assertThatThrownBy(() -> skillController.updateSkill(skillId, request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Skill not found");
    }

    @Test
    void updateSkill_builtinSkill_throwsException() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        var request = new UpdateSkillRequest();
        request.setDescription("Try to update builtin");

        when(skillService.updateSkill(eq(skillId), any(UpdateSkillRequest.class), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("Cannot modify built-in skill"));

        assertThatThrownBy(() -> skillController.updateSkill(skillId, request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot modify built-in skill");
    }

    @Test
    void updateSkill_notAuthorized_throwsException() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        var request = new UpdateSkillRequest();
        request.setDescription("Unauthorized update");

        when(skillService.updateSkill(eq(skillId), any(UpdateSkillRequest.class), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("Not authorized to modify this skill"));

        assertThatThrownBy(() -> skillController.updateSkill(skillId, request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void updateSkill_partialUpdate_returnsOk() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        var request = new UpdateSkillRequest();
        request.setIsEnabled(false);

        var response = SkillDto.builder()
                .id(skillId)
                .name("my_skill")
                .displayName("My Skill")
                .isBuiltin(false)
                .isEnabled(false)
                .build();
        when(skillService.updateSkill(eq(skillId), any(UpdateSkillRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = skillController.updateSkill(skillId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getIsEnabled()).isFalse();
    }

    @Test
    void updateSkill_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var skillId = UUID.randomUUID();
        var request = new UpdateSkillRequest();
        request.setDescription("Updated");

        var response = SkillDto.builder().id(skillId).name("s").build();
        when(skillService.updateSkill(eq(skillId), any(UpdateSkillRequest.class), eq(userId)))
                .thenReturn(response);

        skillController.updateSkill(skillId, request, user);

        verify(skillService).updateSkill(eq(skillId), any(UpdateSkillRequest.class), eq(userId));
    }

    // ===== deleteSkill (DELETE /api/skills/{id}) =====

    @Test
    void deleteSkill_success_returnsNoContent() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        doNothing().when(skillService).deleteSkill(eq(skillId), any(UUID.class));

        var result = skillController.deleteSkill(skillId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(result.getBody()).isNull();
        verify(skillService).deleteSkill(eq(skillId), any(UUID.class));
    }

    @Test
    void deleteSkill_notFound_throwsException() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Skill not found: " + skillId))
                .when(skillService).deleteSkill(eq(skillId), any(UUID.class));

        assertThatThrownBy(() -> skillController.deleteSkill(skillId, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Skill not found");
    }

    @Test
    void deleteSkill_builtinSkill_throwsException() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Cannot delete built-in skill"))
                .when(skillService).deleteSkill(eq(skillId), any(UUID.class));

        assertThatThrownBy(() -> skillController.deleteSkill(skillId, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot delete built-in skill");
    }

    @Test
    void deleteSkill_notAuthorized_throwsException() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Not authorized to delete this skill"))
                .when(skillService).deleteSkill(eq(skillId), any(UUID.class));

        assertThatThrownBy(() -> skillController.deleteSkill(skillId, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void deleteSkill_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var skillId = UUID.randomUUID();
        doNothing().when(skillService).deleteSkill(eq(skillId), eq(userId));

        skillController.deleteSkill(skillId, user);

        verify(skillService).deleteSkill(eq(skillId), eq(userId));
    }

    // ===== executeSkill (POST /api/skills/{id}/execute) =====

    @Test
    void executeSkill_success_returnsOkWithData() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        var request = new ExecuteSkillRequest();
        request.setInput(Map.of("text", "hello world"));

        var skillResult = SkillResult.success(Map.of("result", "parsed"));
        when(skillService.executeSkill(eq(skillId), eq(Map.of("text", "hello world")), any(UUID.class)))
                .thenReturn(skillResult);

        var result = skillController.executeSkill(skillId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("success")).isEqualTo(true);
        assertThat(result.getBody().get("data")).isEqualTo(Map.of("result", "parsed"));
    }

    @Test
    void executeSkill_failure_returnsOkWithError() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        var request = new ExecuteSkillRequest();
        request.setInput(Map.of("invalid", "data"));

        var skillResult = SkillResult.failure("PARSE_ERROR", "Invalid input format");
        when(skillService.executeSkill(eq(skillId), eq(Map.of("invalid", "data")), any(UUID.class)))
                .thenReturn(skillResult);

        var result = skillController.executeSkill(skillId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("success")).isEqualTo(false);
        assertThat(result.getBody().get("error")).isEqualTo("Invalid input format");
        assertThat(result.getBody().get("errorCode")).isEqualTo("PARSE_ERROR");
    }

    @Test
    void executeSkill_failureWithoutErrorCode_returnsEmptyStringErrorCode() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        var request = new ExecuteSkillRequest();
        request.setInput(Map.of("key", "value"));

        var skillResult = SkillResult.failure("Something went wrong");
        when(skillService.executeSkill(eq(skillId), any(), any(UUID.class)))
                .thenReturn(skillResult);

        var result = skillController.executeSkill(skillId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("success")).isEqualTo(false);
        assertThat(result.getBody().get("error")).isEqualTo("Something went wrong");
        assertThat(result.getBody().get("errorCode")).isEqualTo("");
    }

    @Test
    void executeSkill_nullInput_passesNullToService() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        var request = new ExecuteSkillRequest();
        // input is null by default

        var skillResult = SkillResult.success(Map.of("result", "ok"));
        when(skillService.executeSkill(eq(skillId), isNull(), any(UUID.class)))
                .thenReturn(skillResult);

        var result = skillController.executeSkill(skillId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().get("success")).isEqualTo(true);
        verify(skillService).executeSkill(eq(skillId), isNull(), any(UUID.class));
    }

    @Test
    void executeSkill_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var skillId = UUID.randomUUID();
        var request = new ExecuteSkillRequest();
        request.setInput(Map.of("key", "value"));

        var skillResult = SkillResult.success(Map.of("result", "ok"));
        when(skillService.executeSkill(eq(skillId), any(), eq(userId)))
                .thenReturn(skillResult);

        skillController.executeSkill(skillId, request, user);

        verify(skillService).executeSkill(eq(skillId), any(), eq(userId));
    }

    @Test
    void executeSkill_serviceThrowsException_propagates() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        var request = new ExecuteSkillRequest();
        request.setInput(Map.of("key", "value"));

        when(skillService.executeSkill(eq(skillId), any(), any(UUID.class)))
                .thenThrow(new RuntimeException("Execution engine failure"));

        assertThatThrownBy(() -> skillController.executeSkill(skillId, request, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Execution engine failure");
    }

    // ===== executeSkillByName (POST /api/skills/name/{name}/execute) =====

    @Test
    void executeSkillByName_success_returnsOkWithData() {
        var user = testUser();
        var request = new ExecuteSkillRequest();
        request.setInput(Map.of("url", "https://example.com"));

        var skillResult = SkillResult.success(Map.of("status", 200, "body", "OK"));
        when(skillService.executeSkillByName(eq("fetch_url"), eq(Map.of("url", "https://example.com")), any(UUID.class)))
                .thenReturn(skillResult);

        var result = skillController.executeSkillByName("fetch_url", request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("success")).isEqualTo(true);
        assertThat(result.getBody().get("data")).isEqualTo(Map.of("status", 200, "body", "OK"));
    }

    @Test
    void executeSkillByName_failure_returnsOkWithError() {
        var user = testUser();
        var request = new ExecuteSkillRequest();
        request.setInput(Map.of("url", "invalid"));

        var skillResult = SkillResult.failure("FETCH_ERROR", "Invalid URL");
        when(skillService.executeSkillByName(eq("fetch_url"), any(), any(UUID.class)))
                .thenReturn(skillResult);

        var result = skillController.executeSkillByName("fetch_url", request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("success")).isEqualTo(false);
        assertThat(result.getBody().get("error")).isEqualTo("Invalid URL");
        assertThat(result.getBody().get("errorCode")).isEqualTo("FETCH_ERROR");
    }

    @Test
    void executeSkillByName_failureWithoutErrorCode_returnsEmptyStringErrorCode() {
        var user = testUser();
        var request = new ExecuteSkillRequest();
        request.setInput(Map.of("data", "test"));

        var skillResult = SkillResult.failure("General failure");
        when(skillService.executeSkillByName(eq("some_skill"), any(), any(UUID.class)))
                .thenReturn(skillResult);

        var result = skillController.executeSkillByName("some_skill", request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("success")).isEqualTo(false);
        assertThat(result.getBody().get("errorCode")).isEqualTo("");
    }

    @Test
    void executeSkillByName_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var request = new ExecuteSkillRequest();
        request.setInput(Map.of("key", "value"));

        var skillResult = SkillResult.success(Map.of("result", "ok"));
        when(skillService.executeSkillByName(eq("test_skill"), any(), eq(userId)))
                .thenReturn(skillResult);

        skillController.executeSkillByName("test_skill", request, user);

        verify(skillService).executeSkillByName(eq("test_skill"), any(), eq(userId));
    }

    @Test
    void executeSkillByName_serviceThrowsException_propagates() {
        var user = testUser();
        var request = new ExecuteSkillRequest();
        request.setInput(Map.of("key", "value"));

        when(skillService.executeSkillByName(eq("bad_skill"), any(), any(UUID.class)))
                .thenThrow(new RuntimeException("Skill not found"));

        assertThatThrownBy(() -> skillController.executeSkillByName("bad_skill", request, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Skill not found");
    }

    @Test
    void executeSkillByName_nullInput_passesNullToService() {
        var user = testUser();
        var request = new ExecuteSkillRequest();
        // input is null by default

        var skillResult = SkillResult.success(Map.of("result", "ok"));
        when(skillService.executeSkillByName(eq("test_skill"), isNull(), any(UUID.class)))
                .thenReturn(skillResult);

        var result = skillController.executeSkillByName("test_skill", request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(skillService).executeSkillByName(eq("test_skill"), isNull(), any(UUID.class));
    }

    // ===== Response structure validation =====

    @Test
    void executeSkill_successResponse_containsSuccessAndDataKeys() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        var request = new ExecuteSkillRequest();
        request.setInput(Map.of("x", 1));

        var data = Map.<String, Object>of("computed", 42);
        var skillResult = SkillResult.success(data);
        when(skillService.executeSkill(eq(skillId), any(), any(UUID.class)))
                .thenReturn(skillResult);

        var result = skillController.executeSkill(skillId, request, user);

        assertThat(result.getBody()).containsKey("success");
        assertThat(result.getBody()).containsKey("data");
        assertThat(result.getBody()).doesNotContainKey("error");
        assertThat(result.getBody()).doesNotContainKey("errorCode");
    }

    @Test
    void executeSkill_failureResponse_containsSuccessErrorAndErrorCodeKeys() {
        var user = testUser();
        var skillId = UUID.randomUUID();
        var request = new ExecuteSkillRequest();
        request.setInput(Map.of("x", 1));

        var skillResult = SkillResult.failure("ERR_001", "Some error");
        when(skillService.executeSkill(eq(skillId), any(), any(UUID.class)))
                .thenReturn(skillResult);

        var result = skillController.executeSkill(skillId, request, user);

        assertThat(result.getBody()).containsKey("success");
        assertThat(result.getBody()).containsKey("error");
        assertThat(result.getBody()).containsKey("errorCode");
        assertThat(result.getBody()).doesNotContainKey("data");
    }

    // ===== Edge case: multiple skills in list =====

    @Test
    void getSkills_multipleItems_returnsAll() {
        var user = testUser();
        var skills = List.of(
                SkillDto.builder().id(UUID.randomUUID()).name("skill_1").category("data").build(),
                SkillDto.builder().id(UUID.randomUUID()).name("skill_2").category("web").build(),
                SkillDto.builder().id(UUID.randomUUID()).name("skill_3").category("notify").build()
        );
        when(skillService.getAccessibleSkills(any(UUID.class))).thenReturn(skills);

        var result = skillController.getSkills(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(3);
        assertThat(result.getBody())
                .extracting(SkillDto::getName)
                .containsExactly("skill_1", "skill_2", "skill_3");
    }
}
