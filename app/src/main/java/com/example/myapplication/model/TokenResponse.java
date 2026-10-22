package com.example.myapplication.model;

/**
 * 登录响应（POST /api/v1/auth/login → TokenResponse）
 * 数据字典：access_token / token_type / expires_in / role
 */
public class TokenResponse {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private String role;

    public TokenResponse() {
    }

    public TokenResponse(String accessToken, String tokenType, long expiresIn, String role) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.role = role;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
