package com.tech.orbi.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RouteUpdateDto(
        @JsonAlias({"name", "routeName", "code"})
        String name
) {}
