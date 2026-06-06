package com.aiflow.service.impl;

import com.aiflow.enums.FormStatus;
import com.aiflow.model.FormDefinition;
import com.aiflow.repository.FormDefinitionRepository;
import com.aiflow.service.FormDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class FormDefinitionServiceImpl implements FormDefinitionService {

    private final FormDefinitionRepository formDefinitionRepository;

    @Override
    public FormDefinition createForm(FormDefinition form) {
        if (form == null) {
            throw new IllegalArgumentException("form must not be null");
        }
        requireText(form.getFormCode(), "formCode must not be blank");
        requireText(form.getFormName(), "formName must not be blank");

        if (form.getVersion() == null) {
            form.setVersion(1);
        }
        if (formDefinitionRepository.existsByFormCodeAndVersion(form.getFormCode(), form.getVersion())) {
            throw new IllegalStateException("formCode and version already exist");
        }

        LocalDateTime now = LocalDateTime.now();
        if (form.getStatus() == null) {
            form.setStatus(FormStatus.DRAFT);
        }
        form.setDeleted(0);
        form.setCreatedAt(now);
        form.setUpdatedAt(now);
        return formDefinitionRepository.save(form);
    }

    @Override
    public FormDefinition updateForm(Long id, FormDefinition form) {
        requireId(id, "id must not be null");
        if (form == null) {
            throw new IllegalArgumentException("form must not be null");
        }

        FormDefinition existing = getRequiredForm(id);
        if (existing.getStatus() != FormStatus.DRAFT) {
            throw new IllegalStateException("only draft form can be updated");
        }

        existing.setFormName(form.getFormName());
        existing.setBizTypeId(form.getBizTypeId());
        existing.setFieldList(form.getFieldList());
        existing.setFormSchema(form.getFormSchema());
        existing.setUpdatedAt(LocalDateTime.now());
        return formDefinitionRepository.save(existing);
    }

    @Override
    public FormDefinition publishForm(Long id) {
        requireId(id, "id must not be null");
        FormDefinition existing = getRequiredForm(id);
        if (existing.getStatus() != FormStatus.DRAFT) {
            throw new IllegalStateException("only draft form can be published");
        }

        LocalDateTime now = LocalDateTime.now();
        existing.setStatus(FormStatus.PUBLISHED);
        existing.setPublishedAt(now);
        existing.setUpdatedAt(now);
        return formDefinitionRepository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FormDefinition> listPublishedForms() {
        return formDefinitionRepository.findByStatusAndDeletedOrderByUpdatedAtDesc(FormStatus.PUBLISHED, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FormDefinition> findById(Long id) {
        requireId(id, "id must not be null");
        return formDefinitionRepository.findById(id);
    }

    private FormDefinition getRequiredForm(Long id) {
        return formDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("form not found"));
    }

    private void requireId(Long id, String message) {
        if (id == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }
}
