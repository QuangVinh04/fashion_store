package com.fashionstore.identity.service;

import com.fashionstore.identity.dto.staff.CreateStaffRequest;
import com.fashionstore.identity.dto.staff.StaffResponse;

public interface StaffAccountService {

    StaffResponse createStaff(CreateStaffRequest request);
}
