package com.cloudeagle.integration.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "external_auth_token")
public class ExternalAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String externalSystem;

    @Lob
    private String accessToken;

    @Lob
    private String refreshToken;

    private Instant expiresAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExternalSystem() { return externalSystem; }
    public void setExternalSystem(String externalSystem) { this.externalSystem = externalSystem; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}