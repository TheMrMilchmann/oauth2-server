package com.gw2auth.oauth2.server.service.security;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SessionMetadata(@JsonProperty("countryCode") String countryCode,
                              @JsonProperty("city") String city,
                              @JsonProperty("lat") double latitude,
                              @JsonProperty("lng") double longitude) {

}