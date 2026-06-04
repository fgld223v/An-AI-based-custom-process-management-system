package com.aiflow.service;

import org.springframework.stereotype.Service;

@Service
public class AiService {

    public String generateProcess(String scene) {
        return "Mock AI generated process for scene: " + scene;
    }
}
