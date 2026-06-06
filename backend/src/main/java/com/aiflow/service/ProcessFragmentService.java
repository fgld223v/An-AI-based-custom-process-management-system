package com.aiflow.service;

import com.aiflow.model.ProcessFragment;

import java.util.List;
import java.util.Optional;

public interface ProcessFragmentService {

    ProcessFragment createFragment(ProcessFragment fragment);

    ProcessFragment updateFragment(Long id, ProcessFragment fragment);

    ProcessFragment publishFragment(Long id);

    List<ProcessFragment> listFragments();

    Optional<ProcessFragment> findById(Long id);
}
