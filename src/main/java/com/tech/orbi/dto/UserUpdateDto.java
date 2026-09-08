package com.tech.orbi.dto;

import com.tech.orbi.entity.Driver;
import com.tech.orbi.entity.Role;

import java.util.List;
import java.util.Set;

public record UserUpdateDto(String name, String email, Set<Role> role) {
}
