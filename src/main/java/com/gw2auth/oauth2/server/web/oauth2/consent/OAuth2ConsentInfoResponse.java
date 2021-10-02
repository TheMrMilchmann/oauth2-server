package com.gw2auth.oauth2.server.web.oauth2.consent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gw2auth.oauth2.server.service.Gw2ApiPermission;
import com.gw2auth.oauth2.server.service.apitoken.ApiToken;
import com.gw2auth.oauth2.server.web.client.authorization.ClientRegistrationPublicResponse;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Set;

public record OAuth2ConsentInfoResponse(@JsonProperty("clientRegistration") ClientRegistrationPublicResponse clientRegistration,
                                        @JsonProperty("requestedGw2ApiPermissions") Set<Gw2ApiPermission> requestedGw2ApiPermissions,
                                        @JsonProperty("submitFormUri") String submitFormUri,
                                        @JsonProperty("submitFormParameters") MultiValueMap<String, String> submitFormParameters,
                                        @JsonProperty("cancelUri") String cancelUri,
                                        @JsonProperty("apiTokensWithSufficientPermissions") List<ApiTokenResponse> apiTokensWithSufficientPermissionResponses,
                                        @JsonProperty("apiTokensWithInsufficientPermissions") List<ApiTokenResponse> apiTokensWithInsufficientPermissionResponses) {

    public record ApiTokenResponse(@JsonProperty("gw2AccountId") String gw2AccountId,
                                   @JsonProperty("gw2ApiToken") String gw2ApiToken,
                                   @JsonProperty("displayName") String displayName) {

        public static ApiTokenResponse create(ApiToken value) {
            return new ApiTokenResponse(value.gw2AccountId(), value.gw2ApiToken(), value.displayName());
        }
    }
}