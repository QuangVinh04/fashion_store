package com.fashionstore.catalog.service.impl;
import com.fashionstore.catalog.dto.*;
import com.fashionstore.catalog.entity.*;
import com.fashionstore.catalog.entity.enumeration.*;
import com.fashionstore.common.exception.AppException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReturnEvidenceAccessTest extends MediaFileServiceImplTest {
    @Test void returnPhotoUploadIsPrivateImmutableAndNeverUsesPublicBucketKey() {
        requireEvidencePurpose();
        when(currentUserProvider.getCurrentUserId()).thenReturn("owner");
        when(mediaFileRepository.save(any())).thenAnswer(inv -> { MediaFile row=inv.getArgument(0);row.setId("photo");return row; });
        when(storageService.presignUpload(anyString(),eq("image/png"),anyMap())).thenReturn(new PresignedUpload("signed-put",900));
        var input=PresignUploadRequest.builder().filename("parcel.png").contentType("image/png").sizeBytes(1024L).visibility(MediaVisibility.PUBLIC).build();
        var upload=mediaFileService.presignUpload(input,MediaPurpose.valueOf("RETURN_EVIDENCE"));
        assertThat(upload.getStorageKey()).startsWith("evidence/");assertThat(upload.getUploadHeaders()).containsEntry("If-None-Match","*");
        var capture=org.mockito.ArgumentCaptor.forClass(MediaFile.class);verify(mediaFileRepository).save(capture.capture());
        assertThat(capture.getValue().getVisibility()).isEqualTo(MediaVisibility.PRIVATE);
    }
    @Test void activeEvidenceMetadataHasNoPermanentPublicUrl() {
        requireEvidencePurpose();when(currentUserProvider.getCurrentUserId()).thenReturn("owner");
        var photo=photo();when(mediaFileRepository.findById("photo")).thenReturn(Optional.of(photo));
        assertThat(mediaFileService.getById("photo").getUrl()).isNull();
    }
    @Test void foreignEvidenceIsNeverReadableThroughOwnerMediaApi() {
        requireEvidencePurpose();when(currentUserProvider.getCurrentUserId()).thenReturn("foreign");
        when(mediaFileRepository.findById("photo")).thenReturn(Optional.of(photo()));
        assertThatThrownBy(() -> mediaFileService.getById("photo")).isInstanceOf(AppException.class);verifyNoInteractions(storageService);
    }
    @Test void genericMediaUpdateCannotPublishEvidence() {
        requireEvidencePurpose();when(currentUserProvider.getCurrentUserId()).thenReturn("owner");
        when(mediaFileRepository.findById("photo")).thenReturn(Optional.of(photo()));
        var update=MediaFileUpdateRequest.builder().visibility(MediaVisibility.PUBLIC).build();
        assertThatThrownBy(() -> mediaFileService.update("photo",update)).isInstanceOf(AppException.class);
    }
    private MediaFile photo() {
        var photo=MediaFile.builder().ownerId("owner").purpose(MediaPurpose.valueOf("RETURN_EVIDENCE")).visibility(MediaVisibility.PRIVATE)
                .status(MediaStatus.ACTIVE).storageKey("evidence/owner/photo.png").contentType("image/png").sizeBytes(1024L).build();photo.setId("photo");return photo;
    }
    private void requireEvidencePurpose() { assertThat(Arrays.stream(MediaPurpose.values()).map(Enum::name).toList()).as("Dedicated private evidence purpose exists").contains("RETURN_EVIDENCE"); }
}
