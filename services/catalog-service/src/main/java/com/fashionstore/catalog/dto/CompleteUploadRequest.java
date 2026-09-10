package com.fashionstore.catalog.dto;

import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Backend khong con doc bytes nen kich thuoc anh do FE gui kem, tuy chon. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CompleteUploadRequest {

    @Positive
    Integer width;

    @Positive
    Integer height;
}
