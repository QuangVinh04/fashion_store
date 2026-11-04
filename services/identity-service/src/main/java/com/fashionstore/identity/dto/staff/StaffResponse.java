package com.fashionstore.identity.dto.staff;

import com.fashionstore.identity.dto.group.GroupResponse;
import java.util.List;

public record StaffResponse(String id, String email, String fullName, List<GroupResponse> groups) {
}
