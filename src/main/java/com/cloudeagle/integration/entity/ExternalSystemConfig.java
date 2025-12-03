package com.cloudeagle.integration.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "external_system_config")
public class ExternalSystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String name;

    private String baseUrl;
    private String userEndpoint;
    private String authType;

    @Lob
    private String authConfigJson;

    @Lob
    private String paginationConfigJson;

    public String getAuthConfigJson() {
        return authConfigJson;
    }

    public void setAuthConfigJson(String authConfigJson) {
        this.authConfigJson = authConfigJson;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUserEndpoint() {
        return userEndpoint;
    }

    public void setUserEndpoint(String userEndpoint) {
        this.userEndpoint = userEndpoint;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getPaginationConfigJson() {
        return paginationConfigJson;
    }

    public void setPaginationConfigJson(String paginationConfigJson) {
        this.paginationConfigJson = paginationConfigJson;
    }
}