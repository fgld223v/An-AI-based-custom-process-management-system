package com.aiflow.service.impl;

import com.aiflow.model.BizTypeDict;
import com.aiflow.repository.BizTypeDictRepository;
import com.aiflow.service.BizTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class BizTypeServiceImpl implements BizTypeService {

    private final BizTypeDictRepository bizTypeDictRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BizTypeDict> listEnabledBizTypes() {
        return bizTypeDictRepository.findByDeletedAndEnabledOrderBySortOrderAsc(0, 1);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BizTypeDict> findByTypeCode(String typeCode) {
        requireText(typeCode, "typeCode must not be blank");
        return bizTypeDictRepository.findByTypeCode(typeCode);
    }

    private void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }
}
