package com.aiflow.config;

import com.aiflow.model.FormDefinition;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.FormDefinitionRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FormOwnershipInitializerTest {

    @Test
    void assignsLegacyFormWhenAllReferencesHaveOneOwner() throws Exception {
        FormDefinitionRepository formRepository = mock(FormDefinitionRepository.class);
        ProcessTemplateRepository templateRepository = mock(ProcessTemplateRepository.class);
        FormDefinition form = FormDefinition.builder().id(7L).deleted(0).build();
        ProcessTemplate template = ProcessTemplate.builder()
                .createdBy(12L)
                .nodeConfig("{\"Apply\":{\"formId\":7}}")
                .deleted(0)
                .build();
        when(formRepository.findByCreatedByIsNullAndDeleted(0)).thenReturn(List.of(form));
        when(templateRepository.findByDeletedOrderByUpdatedAtDesc(0)).thenReturn(List.of(template));

        new FormOwnershipInitializer(formRepository, templateRepository, new ObjectMapper()).run();

        assertThat(form.getCreatedBy()).isEqualTo(12L);
        assertThat(form.getSourceType()).isEqualTo("legacy");
        verify(formRepository).save(form);
    }
}
