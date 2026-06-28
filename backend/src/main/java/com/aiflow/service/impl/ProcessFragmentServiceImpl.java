package com.aiflow.service.impl;

import com.aiflow.enums.FragmentStatus;
import com.aiflow.model.ProcessFragment;
import com.aiflow.repository.ProcessFragmentRepository;
import com.aiflow.service.ProcessFragmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 流程片段服务实现，负责片段的创建、更新、发布与查询等生命周期管理。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProcessFragmentServiceImpl implements ProcessFragmentService {

    private final ProcessFragmentRepository processFragmentRepository;

    @Override
    public ProcessFragment createFragment(ProcessFragment fragment) {
        if (fragment == null) {
            throw new IllegalArgumentException("fragment must not be null");
        }
        requireText(fragment.getFragmentCode(), "fragmentCode must not be blank");
        requireText(fragment.getFragmentName(), "fragmentName must not be blank");
        if (processFragmentRepository.existsByFragmentCode(fragment.getFragmentCode())) {
            throw new IllegalStateException("fragmentCode already exists");
        }

        LocalDateTime now = LocalDateTime.now();
        if (fragment.getStatus() == null) {
            fragment.setStatus(FragmentStatus.DRAFT);
        }
        fragment.setDeleted(0);
        fragment.setCreatedAt(now);
        fragment.setUpdatedAt(now);
        return processFragmentRepository.save(fragment);
    }

    @Override
    public ProcessFragment updateFragment(Long id, ProcessFragment fragment) {
        requireId(id, "id must not be null");
        if (fragment == null) {
            throw new IllegalArgumentException("fragment must not be null");
        }

        ProcessFragment existing = getRequiredFragment(id);
        if (existing.getStatus() != FragmentStatus.DRAFT) {
            throw new IllegalStateException("only draft fragment can be updated");
        }

        existing.setFragmentName(fragment.getFragmentName());
        existing.setBizTypeId(fragment.getBizTypeId());
        existing.setDescription(fragment.getDescription());
        existing.setFragmentType(fragment.getFragmentType());
        existing.setBpmnXml(fragment.getBpmnXml());
        existing.setNodeConfig(fragment.getNodeConfig());
        existing.setUpdatedAt(LocalDateTime.now());
        return processFragmentRepository.save(existing);
    }

    @Override
    public ProcessFragment publishFragment(Long id) {
        requireId(id, "id must not be null");
        ProcessFragment existing = getRequiredFragment(id);
        if (existing.getStatus() != FragmentStatus.DRAFT) {
            throw new IllegalStateException("only draft fragment can be published");
        }

        LocalDateTime now = LocalDateTime.now();
        existing.setStatus(FragmentStatus.PUBLISHED);
        existing.setPublishedAt(now);
        existing.setUpdatedAt(now);
        return processFragmentRepository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessFragment> listFragments() {
        return processFragmentRepository.findByDeletedOrderByUpdatedAtDesc(0);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessFragment> findById(Long id) {
        requireId(id, "id must not be null");
        return processFragmentRepository.findById(id);
    }

    private ProcessFragment getRequiredFragment(Long id) {
        return processFragmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("fragment not found"));
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
