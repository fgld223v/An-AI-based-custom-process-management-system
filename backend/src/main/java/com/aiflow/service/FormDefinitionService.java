package com.aiflow.service;

import com.aiflow.model.FormDefinition;

import java.util.List;
import java.util.Optional;

public interface FormDefinitionService {

    FormDefinition createForm(FormDefinition form);

    FormDefinition updateForm(Long id, FormDefinition form);

    FormDefinition publishForm(Long id);

    List<FormDefinition> listPublishedForms();

    Optional<FormDefinition> findById(Long id);
}
