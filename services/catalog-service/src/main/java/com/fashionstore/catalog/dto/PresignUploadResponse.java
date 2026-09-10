package com.fashionstore.catalog.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PresignUploadResponse {

    String mediaId;
    String storageKey;
    String uploadUrl;
    /** Browser phai gui dung header nay khi PUT, neu khong chu ky khong khop. */
    String contentType;
    int expiresInSeconds;
}
