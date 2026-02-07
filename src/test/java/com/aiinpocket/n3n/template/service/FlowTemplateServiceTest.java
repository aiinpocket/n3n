package com.aiinpocket.n3n.template.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.flow.dto.FlowResponse;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.aiinpocket.n3n.template.dto.CreateTemplateRequest;
import com.aiinpocket.n3n.template.dto.TemplateResponse;
import com.aiinpocket.n3n.template.entity.FlowTemplate;
import com.aiinpocket.n3n.template.repository.FlowTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FlowTemplateServiceTest extends BaseServiceTest {

    @Mock
    private FlowTemplateRepository templateRepository;

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private FlowVersionRepository flowVersionRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private FlowTemplateService flowTemplateService;

    private UUID userId;
    private UUID templateId;
    private FlowTemplate testTemplate;
    private Map<String, Object> testDefinition;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        templateId = UUID.randomUUID();
        testDefinition = Map.of("nodes", List.of(), "edges", List.of());
        testTemplate = FlowTemplate.builder()
            .id(templateId)
            .name("Test Template")
            .description("A test template")
            .category("automation")
            .tags(List.of("test", "demo"))
            .definition(testDefinition)
            .createdBy(userId)
            .usageCount(5)
            .isOfficial(false)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("List Templates")
    class ListTemplates {

        @Test
        void listTemplates_returnsSummaryPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<FlowTemplate> page = new PageImpl<>(List.of(testTemplate));
            when(templateRepository.findAllByOrderByUsageCountDesc(pageable)).thenReturn(page);

            Page<TemplateResponse> result = flowTemplateService.listTemplates(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Test Template");
        }

        @Test
        void listTemplatesByCategory_filtersCorrectly() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<FlowTemplate> page = new PageImpl<>(List.of(testTemplate));
            when(templateRepository.findByCategoryOrderByUsageCountDesc("automation", pageable)).thenReturn(page);

            Page<TemplateResponse> result = flowTemplateService.listTemplatesByCategory("automation", pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        void listMyTemplates_returnsUserTemplates() {
            when(templateRepository.findByCreatedByOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(testTemplate));

            List<TemplateResponse> result = flowTemplateService.listMyTemplates(userId);

            assertThat(result).hasSize(1);
        }

        @Test
        void listCategories_returnsCategories() {
            when(templateRepository.findAllCategories()).thenReturn(List.of("automation", "integration"));

            List<String> result = flowTemplateService.listCategories();

            assertThat(result).containsExactly("automation", "integration");
        }
    }

    @Nested
    @DisplayName("Search Templates")
    class SearchTemplates {

        @Test
        void searchTemplates_withQuery_searches() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<FlowTemplate> page = new PageImpl<>(List.of(testTemplate));
            when(templateRepository.searchTemplates("test", pageable)).thenReturn(page);

            Page<TemplateResponse> result = flowTemplateService.searchTemplates("test", pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        void searchTemplates_emptyQuery_listsAll() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<FlowTemplate> page = new PageImpl<>(List.of(testTemplate));
            when(templateRepository.findAllByOrderByUsageCountDesc(pageable)).thenReturn(page);

            Page<TemplateResponse> result = flowTemplateService.searchTemplates("", pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        void searchTemplates_nullQuery_listsAll() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<FlowTemplate> page = new PageImpl<>(List.of(testTemplate));
            when(templateRepository.findAllByOrderByUsageCountDesc(pageable)).thenReturn(page);

            Page<TemplateResponse> result = flowTemplateService.searchTemplates(null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Get Template")
    class GetTemplate {

        @Test
        void getTemplate_existing_returnsFullResponse() {
            when(templateRepository.findById(templateId)).thenReturn(Optional.of(testTemplate));

            TemplateResponse result = flowTemplateService.getTemplate(templateId);

            assertThat(result.getName()).isEqualTo("Test Template");
            assertThat(result.getDefinition()).isNotNull();
        }

        @Test
        void getTemplate_nonExisting_throwsException() {
            UUID nonExisting = UUID.randomUUID();
            when(templateRepository.findById(nonExisting)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> flowTemplateService.getTemplate(nonExisting))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Create Template")
    class CreateTemplate {

        @Test
        void createTemplate_validRequest_createsSuccessfully() {
            CreateTemplateRequest request = new CreateTemplateRequest();
            request.setName("New Template");
            request.setDescription("A new template");
            request.setCategory("data");
            request.setDefinition(testDefinition);

            when(templateRepository.save(any(FlowTemplate.class))).thenAnswer(inv -> {
                FlowTemplate t = inv.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
            });

            TemplateResponse result = flowTemplateService.createTemplate(request, userId);

            assertThat(result.getName()).isEqualTo("New Template");
            verify(templateRepository).save(argThat(t ->
                t.getName().equals("New Template") && t.getCreatedBy().equals(userId)
            ));
        }
    }

    @Nested
    @DisplayName("Create Template From Flow")
    class CreateTemplateFromFlow {

        @Test
        void createTemplateFromFlow_validFlowVersion_createsWithFlowDefinition() {
            UUID flowId = UUID.randomUUID();
            Map<String, Object> flowDef = Map.of("nodes", List.of("node1"), "edges", List.of());
            FlowVersion flowVersion = FlowVersion.builder()
                .flowId(flowId)
                .version("1.0.0")
                .definition(flowDef)
                .build();

            CreateTemplateRequest request = new CreateTemplateRequest();
            request.setName("From Flow");
            request.setDescription("desc");

            when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(flowVersion));
            when(templateRepository.save(any(FlowTemplate.class))).thenAnswer(inv -> {
                FlowTemplate t = inv.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
            });

            TemplateResponse result = flowTemplateService.createTemplateFromFlow(flowId, "1.0.0", request, userId);

            assertThat(result.getName()).isEqualTo("From Flow");
            verify(templateRepository).save(argThat(t ->
                t.getDefinition().equals(flowDef)
            ));
        }

        @Test
        void createTemplateFromFlow_nonExistingFlowVersion_throwsException() {
            UUID flowId = UUID.randomUUID();
            CreateTemplateRequest request = new CreateTemplateRequest();
            request.setName("test");

            when(flowVersionRepository.findByFlowIdAndVersion(flowId, "9.9.9"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> flowTemplateService.createTemplateFromFlow(flowId, "9.9.9", request, userId))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Create Flow From Template")
    class CreateFlowFromTemplate {

        @Test
        void createFlowFromTemplate_existingTemplate_createsFlowAndVersion() {
            when(templateRepository.findById(templateId)).thenReturn(Optional.of(testTemplate));
            when(flowRepository.save(any(Flow.class))).thenAnswer(inv -> {
                Flow f = inv.getArgument(0);
                f.setId(UUID.randomUUID());
                return f;
            });
            when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(inv -> inv.getArgument(0));

            FlowResponse result = flowTemplateService.createFlowFromTemplate(templateId, "My New Flow", userId);

            assertThat(result.getName()).isEqualTo("My New Flow");
            verify(flowRepository).save(argThat(f -> f.getName().equals("My New Flow")));
            verify(flowVersionRepository).save(argThat(v ->
                v.getVersion().equals("1.0.0") && v.getDefinition().equals(testDefinition)
            ));
            verify(templateRepository).incrementUsageCount(templateId);
        }

        @Test
        void createFlowFromTemplate_nonExisting_throwsException() {
            UUID nonExisting = UUID.randomUUID();
            when(templateRepository.findById(nonExisting)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> flowTemplateService.createFlowFromTemplate(nonExisting, "test", userId))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Delete Template")
    class DeleteTemplate {

        @Test
        void deleteTemplate_ownedByUser_deletesSuccessfully() {
            when(templateRepository.findById(templateId)).thenReturn(Optional.of(testTemplate));

            flowTemplateService.deleteTemplate(templateId, userId);

            verify(templateRepository).delete(testTemplate);
        }

        @Test
        void deleteTemplate_notOwnedByUser_throwsException() {
            UUID otherUser = UUID.randomUUID();
            when(templateRepository.findById(templateId)).thenReturn(Optional.of(testTemplate));

            assertThatThrownBy(() -> flowTemplateService.deleteTemplate(templateId, otherUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("didn't create");
        }

        @Test
        void deleteTemplate_nonExisting_throwsException() {
            UUID nonExisting = UUID.randomUUID();
            when(templateRepository.findById(nonExisting)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> flowTemplateService.deleteTemplate(nonExisting, userId))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
