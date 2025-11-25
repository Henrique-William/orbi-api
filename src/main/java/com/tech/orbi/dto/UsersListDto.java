package com.tech.orbi.dto;

import java.util.List;

public record UsersListDto(
        List<UserDto> users,
        int page,
        int pageSize,
        int totalPages,
        Long totalElements
) {
}
