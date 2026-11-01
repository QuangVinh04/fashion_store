package com.fashionstore.catalog.media.service.impl;

import com.fashionstore.catalog.media.exception.FileErrorCode;
import com.fashionstore.catalog.media.service.StorageService;
import com.fashionstore.common.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThumbnailServiceImplTest {

    private static final String KEY = "2026/11/photo.jpg";

    @Mock
    StorageService storageService;

    private ThumbnailServiceImpl thumbnails() {
        return new ThumbnailServiceImpl(storageService);
    }

    @Test
    void shrinksALargeImageToTheThumbnailEdgeAndStoresItNextToTheOriginal() throws IOException {
        when(storageService.read(KEY)).thenReturn(image(1200, 800, "jpg"));

        String thumbnailKey = thumbnails().createFor(KEY, "image/jpeg");

        assertThat(thumbnailKey).isEqualTo(KEY + ".thumb.jpg");
        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        verify(storageService).put(org.mockito.ArgumentMatchers.eq(thumbnailKey), stored.capture(),
                org.mockito.ArgumentMatchers.eq("image/jpeg"));
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(stored.getValue()));
        assertThat(thumbnail.getWidth()).isEqualTo(ThumbnailServiceImpl.MAX_EDGE);
        assertThat(thumbnail.getHeight()).isEqualTo(400);
    }

    @Test
    void convertsAPngWithTransparencyToJpeg() throws IOException {
        when(storageService.read("a.png")).thenReturn(image(900, 1500, "png"));

        String thumbnailKey = thumbnails().createFor("a.png", "image/png");

        assertThat(thumbnailKey).isEqualTo("a.png.thumb.jpg");
        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        verify(storageService).put(anyString(), stored.capture(), anyString());
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(stored.getValue()));
        assertThat(thumbnail.getHeight()).isEqualTo(ThumbnailServiceImpl.MAX_EDGE);
        assertThat(thumbnail.getWidth()).isEqualTo(360);
    }

    @Test
    void skipsAnImageThatIsAlreadySmallEnough() throws IOException {
        when(storageService.read(KEY)).thenReturn(image(500, 400, "jpg"));

        assertThat(thumbnails().createFor(KEY, "image/jpeg")).isNull();

        verify(storageService, never()).put(anyString(), any(byte[].class), anyString());
    }

    @Test
    void skipsContentTypesItCannotResizeWithoutReadingTheObject() {
        assertThat(thumbnails().createFor("a.webp", "image/webp")).isNull();
        assertThat(thumbnails().createFor("a.gif", "image/gif")).isNull();
        assertThat(thumbnails().createFor("a.pdf", "application/pdf")).isNull();

        verifyNoInteractions(storageService);
    }

    @Test
    void aCorruptImageNeverFailsTheUpload() {
        when(storageService.read(KEY)).thenReturn("not an image".getBytes());

        assertThat(thumbnails().createFor(KEY, "image/jpeg")).isNull();

        verify(storageService, never()).put(anyString(), any(byte[].class), anyString());
    }

    @Test
    void aStorageFailureNeverFailsTheUpload() {
        when(storageService.read(KEY)).thenThrow(new AppException(FileErrorCode.FILE_STORAGE_FAILED));

        assertThat(thumbnails().createFor(KEY, "image/jpeg")).isNull();
    }

    private static byte[] image(int width, int height, String format) throws IOException {
        BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = source.createGraphics();
        graphics.setColor(java.awt.Color.ORANGE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        BufferedImage encodable = format.equals("png") ? source : toRgb(source);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(encodable, format, bytes);
        return bytes.toByteArray();
    }

    private static BufferedImage toRgb(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(source, 0, 0, null);
        return rgb;
    }
}
