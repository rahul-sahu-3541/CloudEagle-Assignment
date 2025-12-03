package com.cloudeagle.integration.repository;

import com.cloudeagle.integration.entity.ExternalSystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExternalSystemConfigRepository extends JpaRepository<ExternalSystemConfig, Long> {
    Optional<ExternalSystemConfig> findByName(String name);
}
