package com.aiflow.service.impl;

import com.aiflow.enums.FormStatus;
import com.aiflow.model.FormDefinition;
import com.aiflow.repository.FormDefinitionRepository;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.FormDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;

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
        form.setCreatedBy(requireCurrentUserId());
        if (!hasText(form.getSourceType())) {
            form.setSourceType("manual");
        }
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
        assertCanManage(existing);
        requireText(form.getFormName(), "formName must not be blank");

        existing.setFormName(form.getFormName().trim());
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
        assertCanManage(existing);
        if (existing.getStatus() != FormStatus.DRAFT) {
            throw new IllegalStateException("only draft form can be published");
        }
        requireText(existing.getFormName(), "formName must not be blank");
        if (!hasText(existing.getFieldList()) && !hasText(existing.getFormSchema())) {
            throw new IllegalStateException("fieldList or formSchema must not be blank");
        }

        LocalDateTime now = LocalDateTime.now();
        existing.setStatus(FormStatus.PUBLISHED);
        existing.setPublishedAt(now);
        existing.setUpdatedAt(now);
        return formDefinitionRepository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FormDefinition> listForms() {
        if (SecurityUtils.isSuperAdmin()) {
            return formDefinitionRepository.findByDeletedOrderByUpdatedAtDesc(0);
        }
        Long currentUserId = requireCurrentUserId();
        return formDefinitionRepository.findByCreatedByAndDeletedOrderByUpdatedAtDesc(currentUserId, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FormDefinition> listPublishedForms() {
        if (SecurityUtils.isSuperAdmin()) {
            return formDefinitionRepository.findByStatusAndDeletedOrderByUpdatedAtDesc(FormStatus.PUBLISHED, 0);
        }
        Long currentUserId = requireCurrentUserId();
        return formDefinitionRepository.findByCreatedByAndStatusAndDeletedOrderByUpdatedAtDesc(
                currentUserId, FormStatus.PUBLISHED, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FormDefinition> findById(Long id) {
        requireId(id, "id must not be null");
        Optional<FormDefinition> result = formDefinitionRepository.findById(id);
        result.filter(form -> form.getStatus() != FormStatus.PUBLISHED).ifPresent(this::assertCanManage);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FormDefinition> findActiveById(Long id) {
        requireId(id, "id must not be null");
        Optional<FormDefinition> result = formDefinitionRepository.findByIdAndDeleted(id, 0);
        result.filter(form -> form.getStatus() != FormStatus.PUBLISHED).ifPresent(this::assertCanManage);
        return result;
    }

    private FormDefinition getRequiredForm(Long id) {
        return formDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("form not found"));
    }

    @Override
    public void disableForm(Long id) {
        requireId(id, "id must not be null");
        FormDefinition form = getRequiredForm(id);
        assertCanManage(form);
        form.setDeleted(1);
        formDefinitionRepository.save(form);
    }

    private void requireId(Long id, String message) {
        if (id == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private Long requireCurrentUserId() {
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new AccessDeniedException("authenticated user is required");
        }
        return currentUserId;
    }

    private void assertCanManage(FormDefinition form) {
        if (SecurityUtils.isSuperAdmin()) {
            return;
        }
        Long currentUserId = requireCurrentUserId();
        if (form.getCreatedBy() == null || !form.getCreatedBy().equals(currentUserId)) {
            throw new AccessDeniedException("no permission to manage this form");
        }
    }
}
