package com.cloudeagle.integration.repository;

import com.cloudeagle.integration.entity.ExternalAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExternalAuthTokenRepository extends JpaRepository<ExternalAuthToken, Long> {
    Optional<ExternalAuthToken> findByExternalSystem(String externalSystem);
}