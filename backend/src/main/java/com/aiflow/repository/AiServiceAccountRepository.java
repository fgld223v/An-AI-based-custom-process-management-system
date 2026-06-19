package com.aiflow.repository;

import com.aiflow.model.AiServiceAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiServiceAccountRepository extends JpaRepository<AiServiceAccount, Long> {

    Optional<AiServiceAccount> findByApiKey(String apiKey);

    Optional<AiServiceAccount> findByApiKeyAndStatus(String apiKey, String status);
}
