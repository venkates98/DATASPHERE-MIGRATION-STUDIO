package com.datasphere.migration.dto;

import java.util.List;

public record MenuGroup(String label, List<MenuItem> items) {
}
