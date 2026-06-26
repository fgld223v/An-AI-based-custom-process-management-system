package com.aiflow.config;

import com.aiflow.enums.MarketType;
import com.aiflow.model.BizTypeDict;
import com.aiflow.model.Department;
import com.aiflow.model.FormDefinition;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.model.SysUser;
import com.aiflow.model.TemplateMarket;
import com.aiflow.repository.BizTypeDictRepository;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.FormDefinitionRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.repository.TemplateMarketRepository;
import com.aiflow.service.impl.BpmnXmlEnhancer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketTemplateInitializerTest {

    @Test
    void initializesSeedAssetsIdempotentlyAndResolvesDepartmentCodes() throws Exception {
        FormDefinitionRepository formRepository = mock(FormDefinitionRepository.class);
        ProcessTemplateRepository templateRepository = mock(ProcessTemplateRepository.class);
        TemplateMarketRepository marketRepository = mock(TemplateMarketRepository.class);
        BizTypeDictRepository bizTypeRepository = mock(BizTypeDictRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        SysUserRepository userRepository = mock(SysUserRepository.class);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(
                SysUser.builder().id(1L).username("admin").build()));
        when(bizTypeRepository.findByTypeCode(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            long id = switch (code) {
                case "leave" -> 11L;
                case "business_trip" -> 12L;
                case "reimbursement" -> 21L;
                case "purchase" -> 31L;
                case "repair" -> 32L;
                case "inspection" -> 33L;
                case "work_report" -> 42L;
                case "general_approval" -> 43L;
                default -> throw new IllegalArgumentException(code);
            };
            return Optional.of(BizTypeDict.builder().id(id).typeCode(code).build());
        });
        when(departmentRepository.findByDeletedOrderBySortOrder(0)).thenReturn(List.of(
                Department.builder().id(20L).deptCode("finance").build(),
                Department.builder().id(30L).deptCode("hr").build(),
                Department.builder().id(40L).deptCode("purchase").build(),
                Department.builder().id(50L).deptCode("tech").build()));

        Map<String, FormDefinition> forms = new LinkedHashMap<>();
        AtomicLong formIds = new AtomicLong(100);
        when(formRepository.findByFormCodeAndVersion(anyString(), anyInt()))
                .thenAnswer(invocation -> Optional.ofNullable(forms.get(invocation.getArgument(0))));
        when(formRepository.save(any(FormDefinition.class))).thenAnswer(invocation -> {
            FormDefinition form = invocation.getArgument(0);
            form.setId(formIds.incrementAndGet());
            forms.put(form.getFormCode(), form);
            return form;
        });

        Map<String, ProcessTemplate> templates = new LinkedHashMap<>();
        AtomicLong templateIds = new AtomicLong(200);
        when(templateRepository.findByTemplateCodeAndVersion(anyString(), anyInt()))
                .thenAnswer(invocation -> Optional.ofNullable(templates.get(invocation.getArgument(0))));
        when(templateRepository.save(any(ProcessTemplate.class))).thenAnswer(invocation -> {
            ProcessTemplate template = invocation.getArgument(0);
            if (template.getId() == null) {
                template.setId(templateIds.incrementAndGet());
            }
            templates.put(template.getTemplateCode(), template);
            return template;
        });

        Map<Long, TemplateMarket> marketItems = new LinkedHashMap<>();
        when(marketRepository.findByTypeAndSourceIdAndDeleted(any(MarketType.class), anyLong(), anyInt()))
                .thenAnswer(invocation -> Optional.ofNullable(marketItems.get(invocation.getArgument(1))));
        when(marketRepository.save(any(TemplateMarket.class))).thenAnswer(invocation -> {
            TemplateMarket item = invocation.getArgument(0);
            marketItems.put(item.getSourceId(), item);
            return item;
        });

        MarketTemplateInitializer initializer = new MarketTemplateInitializer(
                new DefaultResourceLoader(), new ObjectMapper(), formRepository, templateRepository,
                marketRepository, bizTypeRepository, departmentRepository, userRepository);

        initializer.run();
        templates.get("SYS_LEAVE_REQUEST").setNodeConfig("{}");
        templates.get("SYS_LEAVE_REQUEST").setFormBindConfig("{}");
        initializer.run();

        assertThat(forms).hasSize(8);
        assertThat(templates).hasSize(8);
        assertThat(marketItems).hasSize(8);
        assertThat(templates.get("SYS_EXPENSE_REIMBURSEMENT").getNodeConfig())
                .contains("\\\"departmentId\\\":20")
                .doesNotContain("${dept.");
        assertThat(templates.values()).allSatisfy(template -> {
            assertThat(template.getBpmnXml()).contains("bpmndi:BPMNDiagram");
            assertThat(template.getFormId()).isNotNull();
            assertThat(template.getNodeConfig()).contains("\"formBindingMode\":\"node_form\"")
                    .contains("\"formId\":" + template.getFormId())
                    .contains("\"assigneeType\"")
                    .contains("\"rejectRule\":\"END_PROCESS\"");
            assertThat(template.getFormBindConfig()).contains("\"formId\":" + template.getFormId());
            assertThat(new BpmnXmlEnhancer(new ObjectMapper())
                    .enhance(template.getBpmnXml(), template.getNodeConfig()))
                    .contains("singleAssigneeListener");
        });

        verify(formRepository, org.mockito.Mockito.times(8)).save(any(FormDefinition.class));
        verify(templateRepository, org.mockito.Mockito.times(9)).save(any(ProcessTemplate.class));
        verify(marketRepository, org.mockito.Mockito.times(8)).save(any(TemplateMarket.class));
    }
}
