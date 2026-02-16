package com.aiinpocket.n3n.template.controller;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.flow.dto.FlowResponse;
import com.aiinpocket.n3n.template.dto.CreateTemplateRequest;
import com.aiinpocket.n3n.template.dto.TemplateResponse;
import com.aiinpocket.n3n.template.dto.UpdateTemplateRequest;
import com.aiinpocket.n3n.template.service.FlowTemplateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlowTemplateControllerTest {

    @Mock
    private FlowTemplateService templateService;

    @Mock
    private ActivityService activityService;

    @InjectMocks
    private FlowTemplateController flowTemplateController;

    // ===== Helper methods =====

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

    private TemplateResponse sampleTemplateResponse() {
        return TemplateResponse.builder()
                .id(UUID.randomUUID())
                .name("Sample Template")
                .description("A sample workflow template")
                .category("automation")
                .tags(List.of("api", "http"))
                .definition(Map.of("nodes", List.of(), "edges", List.of()))
                .thumbnailUrl("https://example.com/thumb.png")
                .isOfficial(false)
                .usageCount(5)
                .createdBy(UUID.randomUUID())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private TemplateResponse sampleTemplateResponseWithId(UUID id) {
        return TemplateResponse.builder()
                .id(id)
                .name("Template-" + id.toString().substring(0, 8))
                .description("Template description")
                .category("data")
                .tags(List.of("database", "transform"))
                .definition(Map.of("nodes", List.of()))
                .isOfficial(false)
                .usageCount(0)
                .createdBy(UUID.randomUUID())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private CreateTemplateRequest sampleCreateRequest() {
        var request = new CreateTemplateRequest();
        request.setName("New Template");
        request.setDescription("A new workflow template");
        request.setCategory("automation");
        request.setTags(List.of("api", "webhook"));
        request.setDefinition(Map.of("nodes", List.of(), "edges", List.of()));
        request.setThumbnailUrl("https://example.com/new-thumb.png");
        return request;
    }

    private UpdateTemplateRequest sampleUpdateRequest() {
        var request = new UpdateTemplateRequest();
        request.setName("Updated Template");
        request.setDescription("Updated description");
        request.setCategory("data-processing");
        request.setTags(List.of("data", "etl"));
        return request;
    }

    // ===== listTemplates (GET /api/templates) =====

    @Test
    void listTemplates_noParams_returnsAllTemplates() {
        var template = sampleTemplateResponse();
        Page<TemplateResponse> page = new PageImpl<>(List.of(template));
        when(templateService.listTemplates(any(Pageable.class))).thenReturn(page);

        var result = flowTemplateController.listTemplates(null, null, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        assertThat(result.getBody().getContent().get(0).getName()).isEqualTo("Sample Template");
        verify(templateService).listTemplates(any(Pageable.class));
        verify(templateService, never()).searchTemplates(any(), any());
        verify(templateService, never()).listTemplatesByCategory(any(), any());
    }

    @Test
    void listTemplates_withSearch_callsSearchTemplates() {
        var template = sampleTemplateResponse();
        Page<TemplateResponse> page = new PageImpl<>(List.of(template));
        when(templateService.searchTemplates(eq("api"), any(Pageable.class))).thenReturn(page);

        var result = flowTemplateController.listTemplates(null, "api", Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        verify(templateService).searchTemplates(eq("api"), any(Pageable.class));
        verify(templateService, never()).listTemplates(any(Pageable.class));
        verify(templateService, never()).listTemplatesByCategory(any(), any());
    }

    @Test
    void listTemplates_withCategory_callsListByCategory() {
        var template = sampleTemplateResponse();
        Page<TemplateResponse> page = new PageImpl<>(List.of(template));
        when(templateService.listTemplatesByCategory(eq("automation"), any(Pageable.class))).thenReturn(page);

        var result = flowTemplateController.listTemplates("automation", null, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        verify(templateService).listTemplatesByCategory(eq("automation"), any(Pageable.class));
        verify(templateService, never()).listTemplates(any(Pageable.class));
        verify(templateService, never()).searchTemplates(any(), any());
    }

    @Test
    void listTemplates_withSearchAndCategory_searchTakesPrecedence() {
        var template = sampleTemplateResponse();
        Page<TemplateResponse> page = new PageImpl<>(List.of(template));
        when(templateService.searchTemplates(eq("webhook"), any(Pageable.class))).thenReturn(page);

        var result = flowTemplateController.listTemplates("automation", "webhook", Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(templateService).searchTemplates(eq("webhook"), any(Pageable.class));
        verify(templateService, never()).listTemplatesByCategory(any(), any());
        verify(templateService, never()).listTemplates(any(Pageable.class));
    }

    @Test
    void listTemplates_emptySearch_fallsThroughToCategory() {
        var template = sampleTemplateResponse();
        Page<TemplateResponse> page = new PageImpl<>(List.of(template));
        when(templateService.listTemplatesByCategory(eq("data"), any(Pageable.class))).thenReturn(page);

        var result = flowTemplateController.listTemplates("data", "", Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(templateService).listTemplatesByCategory(eq("data"), any(Pageable.class));
        verify(templateService, never()).searchTemplates(any(), any());
    }

    @Test
    void listTemplates_emptySearchAndEmptyCategory_listsAll() {
        Page<TemplateResponse> page = new PageImpl<>(List.of());
        when(templateService.listTemplates(any(Pageable.class))).thenReturn(page);

        var result = flowTemplateController.listTemplates("", "", Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isZero();
        verify(templateService).listTemplates(any(Pageable.class));
    }

    @Test
    void listTemplates_emptyPage_returnsOk() {
        when(templateService.listTemplates(any(Pageable.class))).thenReturn(Page.empty());

        var result = flowTemplateController.listTemplates(null, null, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isZero();
    }

    @Test
    void listTemplates_multipleItems_returnsAll() {
        var t1 = TemplateResponse.builder().id(UUID.randomUUID()).name("Template A").category("automation").build();
        var t2 = TemplateResponse.builder().id(UUID.randomUUID()).name("Template B").category("data").build();
        var t3 = TemplateResponse.builder().id(UUID.randomUUID()).name("Template C").category("ai").build();
        Page<TemplateResponse> page = new PageImpl<>(List.of(t1, t2, t3));
        when(templateService.listTemplates(any(Pageable.class))).thenReturn(page);

        var result = flowTemplateController.listTemplates(null, null, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isEqualTo(3);
        assertThat(result.getBody().getContent())
                .extracting(TemplateResponse::getName)
                .containsExactly("Template A", "Template B", "Template C");
    }

    @Test
    void listTemplates_withPagination_passesPageableCorrectly() {
        var pageable = PageRequest.of(0, 10);
        var template = sampleTemplateResponse();
        Page<TemplateResponse> page = new PageImpl<>(List.of(template), pageable, 25);
        when(templateService.listTemplates(eq(pageable))).thenReturn(page);

        var result = flowTemplateController.listTemplates(null, null, pageable);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isEqualTo(25);
        assertThat(result.getBody().getNumber()).isEqualTo(0);
        assertThat(result.getBody().getSize()).isEqualTo(10);
        verify(templateService).listTemplates(eq(pageable));
    }

    @Test
    void listTemplates_nullSearch_doesNotCallSearch() {
        when(templateService.listTemplates(any(Pageable.class))).thenReturn(Page.empty());

        flowTemplateController.listTemplates(null, null, Pageable.unpaged());

        verify(templateService, never()).searchTemplates(any(), any());
    }

    // ===== listCategories (GET /api/templates/categories) =====

    @Test
    void listCategories_returnsList() {
        when(templateService.listCategories()).thenReturn(List.of("automation", "data", "ai", "integration"));

        var result = flowTemplateController.listCategories();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(4);
        assertThat(result.getBody()).containsExactly("automation", "data", "ai", "integration");
    }

    @Test
    void listCategories_empty_returnsEmptyList() {
        when(templateService.listCategories()).thenReturn(List.of());

        var result = flowTemplateController.listCategories();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void listCategories_singleCategory_returnsSingleItem() {
        when(templateService.listCategories()).thenReturn(List.of("automation"));

        var result = flowTemplateController.listCategories();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0)).isEqualTo("automation");
    }

    // ===== listMyTemplates (GET /api/templates/mine) =====

    @Test
    void listMyTemplates_returnsUserTemplates() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var template = sampleTemplateResponse();
        when(templateService.listMyTemplates(eq(userId))).thenReturn(List.of(template));

        var result = flowTemplateController.listMyTemplates(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getName()).isEqualTo("Sample Template");
        verify(templateService).listMyTemplates(eq(userId));
    }

    @Test
    void listMyTemplates_empty_returnsEmptyList() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        when(templateService.listMyTemplates(eq(userId))).thenReturn(List.of());

        var result = flowTemplateController.listMyTemplates(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void listMyTemplates_multipleTemplates_returnsAll() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var t1 = TemplateResponse.builder().id(UUID.randomUUID()).name("My Template 1").build();
        var t2 = TemplateResponse.builder().id(UUID.randomUUID()).name("My Template 2").build();
        when(templateService.listMyTemplates(eq(userId))).thenReturn(List.of(t1, t2));

        var result = flowTemplateController.listMyTemplates(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody())
                .extracting(TemplateResponse::getName)
                .containsExactly("My Template 1", "My Template 2");
    }

    @Test
    void listMyTemplates_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        when(templateService.listMyTemplates(eq(userId))).thenReturn(List.of());

        flowTemplateController.listMyTemplates(user);

        verify(templateService).listMyTemplates(eq(userId));
    }

    // ===== getTemplate (GET /api/templates/{id}) =====

    @Test
    void getTemplate_found_returnsOk() {
        var templateId = UUID.randomUUID();
        var template = sampleTemplateResponseWithId(templateId);
        when(templateService.getTemplate(eq(templateId))).thenReturn(template);

        var result = flowTemplateController.getTemplate(templateId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getId()).isEqualTo(templateId);
        assertThat(result.getBody().getCategory()).isEqualTo("data");
    }

    @Test
    void getTemplate_notFound_throwsException() {
        var templateId = UUID.randomUUID();
        when(templateService.getTemplate(eq(templateId)))
                .thenThrow(new ResourceNotFoundException("Template not found: " + templateId));

        assertThatThrownBy(() -> flowTemplateController.getTemplate(templateId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Template not found");
    }

    @Test
    void getTemplate_returnsFullDetails() {
        var templateId = UUID.randomUUID();
        var template = TemplateResponse.builder()
                .id(templateId)
                .name("Detailed Template")
                .description("Full details template")
                .category("automation")
                .tags(List.of("api", "http", "webhook"))
                .definition(Map.of("nodes", List.of(Map.of("id", "node1"))))
                .thumbnailUrl("https://example.com/thumb.png")
                .isOfficial(true)
                .usageCount(42)
                .createdBy(UUID.randomUUID())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(templateService.getTemplate(eq(templateId))).thenReturn(template);

        var result = flowTemplateController.getTemplate(templateId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getName()).isEqualTo("Detailed Template");
        assertThat(result.getBody().getDescription()).isEqualTo("Full details template");
        assertThat(result.getBody().getTags()).containsExactly("api", "http", "webhook");
        assertThat(result.getBody().isOfficial()).isTrue();
        assertThat(result.getBody().getUsageCount()).isEqualTo(42);
        assertThat(result.getBody().getDefinition()).containsKey("nodes");
    }

    // ===== createTemplate (POST /api/templates) =====

    @Test
    void createTemplate_success_returnsCreated() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var request = sampleCreateRequest();
        var response = TemplateResponse.builder()
                .id(UUID.randomUUID())
                .name("New Template")
                .description("A new workflow template")
                .category("automation")
                .tags(List.of("api", "webhook"))
                .createdBy(userId)
                .createdAt(Instant.now())
                .build();
        when(templateService.createTemplate(eq(request), eq(userId))).thenReturn(response);

        var result = flowTemplateController.createTemplate(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("New Template");
        assertThat(result.getBody().getCategory()).isEqualTo("automation");
        assertThat(result.getBody().getCreatedBy()).isEqualTo(userId);
        verify(templateService).createTemplate(eq(request), eq(userId));
    }

    @Test
    void createTemplate_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var request = sampleCreateRequest();
        var response = TemplateResponse.builder()
                .id(UUID.randomUUID())
                .name("New Template")
                .build();
        when(templateService.createTemplate(eq(request), eq(userId))).thenReturn(response);

        flowTemplateController.createTemplate(request, user);

        verify(templateService).createTemplate(eq(request), eq(userId));
    }

    @Test
    void createTemplate_serviceFails_throwsException() {
        var user = testUser();
        var request = sampleCreateRequest();
        when(templateService.createTemplate(any(CreateTemplateRequest.class), any(UUID.class)))
                .thenThrow(new RuntimeException("Database error"));

        assertThatThrownBy(() -> flowTemplateController.createTemplate(request, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database error");
    }

    @Test
    void createTemplate_withAllFields_returnsCreated() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var request = new CreateTemplateRequest();
        request.setName("Full Template");
        request.setDescription("Template with all fields");
        request.setCategory("ai");
        request.setTags(List.of("openai", "chatgpt", "nlp"));
        request.setDefinition(Map.of("nodes", List.of(), "edges", List.of()));
        request.setThumbnailUrl("https://example.com/ai-thumb.png");

        var response = TemplateResponse.builder()
                .id(UUID.randomUUID())
                .name("Full Template")
                .description("Template with all fields")
                .category("ai")
                .tags(List.of("openai", "chatgpt", "nlp"))
                .thumbnailUrl("https://example.com/ai-thumb.png")
                .createdBy(userId)
                .build();
        when(templateService.createTemplate(eq(request), eq(userId))).thenReturn(response);

        var result = flowTemplateController.createTemplate(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getDescription()).isEqualTo("Template with all fields");
        assertThat(result.getBody().getTags()).hasSize(3);
        assertThat(result.getBody().getThumbnailUrl()).isEqualTo("https://example.com/ai-thumb.png");
    }

    // ===== updateTemplate (PUT /api/templates/{id}) =====

    @Test
    void updateTemplate_success_returnsOk() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var templateId = UUID.randomUUID();
        var request = sampleUpdateRequest();
        var response = TemplateResponse.builder()
                .id(templateId)
                .name("Updated Template")
                .description("Updated description")
                .category("data-processing")
                .tags(List.of("data", "etl"))
                .createdBy(userId)
                .build();
        when(templateService.updateTemplate(eq(templateId), eq(request), eq(userId))).thenReturn(response);

        var result = flowTemplateController.updateTemplate(templateId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getId()).isEqualTo(templateId);
        assertThat(result.getBody().getName()).isEqualTo("Updated Template");
        assertThat(result.getBody().getDescription()).isEqualTo("Updated description");
        assertThat(result.getBody().getCategory()).isEqualTo("data-processing");
        verify(templateService).updateTemplate(eq(templateId), eq(request), eq(userId));
    }

    @Test
    void updateTemplate_notFound_throwsException() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var templateId = UUID.randomUUID();
        var request = sampleUpdateRequest();
        when(templateService.updateTemplate(eq(templateId), eq(request), eq(userId)))
                .thenThrow(new ResourceNotFoundException("Template not found: " + templateId));

        assertThatThrownBy(() -> flowTemplateController.updateTemplate(templateId, request, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Template not found");
    }

    @Test
    void updateTemplate_accessDenied_throwsException() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var templateId = UUID.randomUUID();
        var request = sampleUpdateRequest();
        when(templateService.updateTemplate(eq(templateId), eq(request), eq(userId)))
                .thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> flowTemplateController.updateTemplate(templateId, request, user))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void updateTemplate_partialUpdate_returnsOk() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var templateId = UUID.randomUUID();
        var request = new UpdateTemplateRequest();
        request.setName("Only Name Updated");
        // description, category, tags are null (partial update)

        var response = TemplateResponse.builder()
                .id(templateId)
                .name("Only Name Updated")
                .description("Original description")
                .category("automation")
                .build();
        when(templateService.updateTemplate(eq(templateId), eq(request), eq(userId))).thenReturn(response);

        var result = flowTemplateController.updateTemplate(templateId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getName()).isEqualTo("Only Name Updated");
        assertThat(result.getBody().getDescription()).isEqualTo("Original description");
    }

    @Test
    void updateTemplate_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var templateId = UUID.randomUUID();
        var request = sampleUpdateRequest();
        var response = TemplateResponse.builder().id(templateId).name("Updated").build();
        when(templateService.updateTemplate(eq(templateId), eq(request), eq(userId))).thenReturn(response);

        flowTemplateController.updateTemplate(templateId, request, user);

        verify(templateService).updateTemplate(eq(templateId), eq(request), eq(userId));
    }

    // ===== createTemplateFromFlow (POST /api/templates/from-flow/{flowId}/version/{version}) =====

    @Test
    void createTemplateFromFlow_success_returnsCreated() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var flowId = UUID.randomUUID();
        var version = "1.0.0";
        var request = sampleCreateRequest();
        var response = TemplateResponse.builder()
                .id(UUID.randomUUID())
                .name("New Template")
                .description("A new workflow template")
                .category("automation")
                .createdBy(userId)
                .build();
        when(templateService.createTemplateFromFlow(eq(flowId), eq(version), eq(request), eq(userId)))
                .thenReturn(response);

        var result = flowTemplateController.createTemplateFromFlow(flowId, version, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("New Template");
        assertThat(result.getBody().getCreatedBy()).isEqualTo(userId);
        verify(templateService).createTemplateFromFlow(eq(flowId), eq(version), eq(request), eq(userId));
    }

    @Test
    void createTemplateFromFlow_flowNotFound_throwsException() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var flowId = UUID.randomUUID();
        var version = "1.0.0";
        var request = sampleCreateRequest();
        when(templateService.createTemplateFromFlow(eq(flowId), eq(version), eq(request), eq(userId)))
                .thenThrow(new ResourceNotFoundException("Flow not found: " + flowId));

        assertThatThrownBy(() -> flowTemplateController.createTemplateFromFlow(flowId, version, request, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Flow not found");
    }

    @Test
    void createTemplateFromFlow_versionNotFound_throwsException() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var flowId = UUID.randomUUID();
        var version = "99.0.0";
        var request = sampleCreateRequest();
        when(templateService.createTemplateFromFlow(eq(flowId), eq(version), eq(request), eq(userId)))
                .thenThrow(new ResourceNotFoundException("Flow version not found: " + flowId + "/" + version));

        assertThatThrownBy(() -> flowTemplateController.createTemplateFromFlow(flowId, version, request, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Flow version not found");
    }

    @Test
    void createTemplateFromFlow_accessDenied_throwsException() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var flowId = UUID.randomUUID();
        var version = "1.0.0";
        var request = sampleCreateRequest();
        when(templateService.createTemplateFromFlow(eq(flowId), eq(version), eq(request), eq(userId)))
                .thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> flowTemplateController.createTemplateFromFlow(flowId, version, request, user))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void createTemplateFromFlow_differentVersions_passCorrectVersion() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var flowId = UUID.randomUUID();
        var request = sampleCreateRequest();
        var response = TemplateResponse.builder().id(UUID.randomUUID()).name("Template").build();

        when(templateService.createTemplateFromFlow(eq(flowId), eq("2.1.3"), eq(request), eq(userId)))
                .thenReturn(response);

        flowTemplateController.createTemplateFromFlow(flowId, "2.1.3", request, user);

        verify(templateService).createTemplateFromFlow(eq(flowId), eq("2.1.3"), eq(request), eq(userId));
    }

    // ===== createFlowFromTemplate (POST /api/templates/{id}/use) =====

    @Test
    void createFlowFromTemplate_success_returnsCreated() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var templateId = UUID.randomUUID();
        var flowName = "My New Flow";
        var flowResponse = FlowResponse.builder()
                .id(UUID.randomUUID())
                .name(flowName)
                .description("Created from template: Sample Template")
                .createdBy(userId)
                .latestVersion("1.0.0")
                .createdAt(Instant.now())
                .build();
        when(templateService.createFlowFromTemplate(eq(templateId), eq(flowName), eq(userId)))
                .thenReturn(flowResponse);

        var result = flowTemplateController.createFlowFromTemplate(templateId, flowName, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("My New Flow");
        assertThat(result.getBody().getLatestVersion()).isEqualTo("1.0.0");
        assertThat(result.getBody().getCreatedBy()).isEqualTo(userId);
        verify(templateService).createFlowFromTemplate(eq(templateId), eq(flowName), eq(userId));
    }

    @Test
    void createFlowFromTemplate_templateNotFound_throwsException() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var templateId = UUID.randomUUID();
        when(templateService.createFlowFromTemplate(eq(templateId), eq("Test Flow"), eq(userId)))
                .thenThrow(new ResourceNotFoundException("Template not found: " + templateId));

        assertThatThrownBy(() -> flowTemplateController.createFlowFromTemplate(templateId, "Test Flow", user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Template not found");
    }

    @Test
    void createFlowFromTemplate_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var templateId = UUID.randomUUID();
        var flowResponse = FlowResponse.builder()
                .id(UUID.randomUUID())
                .name("Test Flow")
                .build();
        when(templateService.createFlowFromTemplate(eq(templateId), eq("Test Flow"), eq(userId)))
                .thenReturn(flowResponse);

        flowTemplateController.createFlowFromTemplate(templateId, "Test Flow", user);

        verify(templateService).createFlowFromTemplate(eq(templateId), eq("Test Flow"), eq(userId));
    }

    @Test
    void createFlowFromTemplate_returnsFlowResponse() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var templateId = UUID.randomUUID();
        var flowId = UUID.randomUUID();
        var flowResponse = FlowResponse.builder()
                .id(flowId)
                .name("Flow From Template")
                .description("Created from template: API Workflow")
                .createdBy(userId)
                .latestVersion("1.0.0")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(templateService.createFlowFromTemplate(eq(templateId), eq("Flow From Template"), eq(userId)))
                .thenReturn(flowResponse);

        var result = flowTemplateController.createFlowFromTemplate(templateId, "Flow From Template", user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getId()).isEqualTo(flowId);
        assertThat(result.getBody().getDescription()).contains("Created from template");
    }

    // ===== deleteTemplate (DELETE /api/templates/{id}) =====

    @Test
    void deleteTemplate_success_returnsNoContent() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var templateId = UUID.randomUUID();
        doNothing().when(templateService).deleteTemplate(eq(templateId), eq(userId));

        var result = flowTemplateController.deleteTemplate(templateId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(result.getBody()).isNull();
        verify(templateService).deleteTemplate(eq(templateId), eq(userId));
    }

    @Test
    void deleteTemplate_notFound_throwsException() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var templateId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Template not found: " + templateId))
                .when(templateService).deleteTemplate(eq(templateId), eq(userId));

        assertThatThrownBy(() -> flowTemplateController.deleteTemplate(templateId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Template not found");
    }

    @Test
    void deleteTemplate_accessDenied_throwsException() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var templateId = UUID.randomUUID();
        doThrow(new AccessDeniedException("Access denied"))
                .when(templateService).deleteTemplate(eq(templateId), eq(userId));

        assertThatThrownBy(() -> flowTemplateController.deleteTemplate(templateId, user))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void deleteTemplate_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var templateId = UUID.randomUUID();
        doNothing().when(templateService).deleteTemplate(eq(templateId), eq(userId));

        flowTemplateController.deleteTemplate(templateId, user);

        verify(templateService).deleteTemplate(eq(templateId), eq(userId));
    }

    // ===== Cross-cutting: userId extraction verification =====

    @Test
    void allAuthenticatedEndpoints_extractUserIdCorrectly() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);

        // listMyTemplates
        when(templateService.listMyTemplates(eq(userId))).thenReturn(List.of());
        flowTemplateController.listMyTemplates(user);
        verify(templateService).listMyTemplates(eq(userId));

        // createTemplate
        var createRequest = sampleCreateRequest();
        var templateResponse = TemplateResponse.builder().id(UUID.randomUUID()).name("t").build();
        when(templateService.createTemplate(eq(createRequest), eq(userId))).thenReturn(templateResponse);
        flowTemplateController.createTemplate(createRequest, user);
        verify(templateService).createTemplate(eq(createRequest), eq(userId));

        // updateTemplate
        var updateRequest = sampleUpdateRequest();
        var templateId = UUID.randomUUID();
        when(templateService.updateTemplate(eq(templateId), eq(updateRequest), eq(userId))).thenReturn(templateResponse);
        flowTemplateController.updateTemplate(templateId, updateRequest, user);
        verify(templateService).updateTemplate(eq(templateId), eq(updateRequest), eq(userId));

        // deleteTemplate
        doNothing().when(templateService).deleteTemplate(eq(templateId), eq(userId));
        flowTemplateController.deleteTemplate(templateId, user);
        verify(templateService).deleteTemplate(eq(templateId), eq(userId));
    }

    // ===== Edge cases =====

    @Test
    void listTemplates_searchWithSpaces_passesAsIs() {
        Page<TemplateResponse> page = new PageImpl<>(List.of());
        when(templateService.searchTemplates(eq("  api webhook  "), any(Pageable.class))).thenReturn(page);

        var result = flowTemplateController.listTemplates(null, "  api webhook  ", Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(templateService).searchTemplates(eq("  api webhook  "), any(Pageable.class));
    }

    @Test
    void getTemplate_withRandomUUID_callsServiceCorrectly() {
        var templateId = UUID.randomUUID();
        var template = TemplateResponse.builder().id(templateId).name("Random").build();
        when(templateService.getTemplate(eq(templateId))).thenReturn(template);

        var result = flowTemplateController.getTemplate(templateId);

        assertThat(result.getBody().getId()).isEqualTo(templateId);
        verify(templateService).getTemplate(eq(templateId));
    }

    @Test
    void createFlowFromTemplate_serviceFails_throwsException() {
        var user = testUser();
        var userId = UUID.fromString(user.getUsername());
        var templateId = UUID.randomUUID();
        when(templateService.createFlowFromTemplate(eq(templateId), eq("Flow"), eq(userId)))
                .thenThrow(new RuntimeException("Database connection failed"));

        assertThatThrownBy(() -> flowTemplateController.createFlowFromTemplate(templateId, "Flow", user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection failed");
    }
}
