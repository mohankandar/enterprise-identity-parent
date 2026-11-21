package com.documentplatform.identity.spring;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public final class RequiredAudienceValidator implements OAuth2TokenValidator<Jwt> {
    private final String audience;

    public RequiredAudienceValidator(String audience) {
        if (audience == null || audience.isBlank()) throw new IllegalArgumentException("audience is required");
        this.audience = audience.trim();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        // A token without an aud claim yields null here; treat it as "required audience is missing".
        List<String> audiences = token.getAudience();
        return audiences != null && audiences.contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Required API audience is missing.", null));
    }
}
