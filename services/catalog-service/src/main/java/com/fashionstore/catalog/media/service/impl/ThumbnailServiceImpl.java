package com.fashionstore.catalog.media.service.impl;

import com.fashionstore.catalog.media.service.StorageService;
import com.fashionstore.catalog.media.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailServiceImpl implements ThumbnailService {

    /** Du net cho the san pham tren man hinh retina; danh sach admin chi can nho hon. */
    static final int MAX_EDGE = 600;
    private static final String THUMBNAIL_SUFFIX = ".thumb.jpg";
    private static final String THUMBNAIL_CONTENT_TYPE = "image/jpeg";
    /** WebP/AVIF khong co reader trong JDK, GIF co the la anh dong: de nguyen anh goc. */
    private static final Set<String> RESIZABLE_TYPES = Set.of("image/jpeg", "image/png");
    /** Chan anh "bom giai nen" (PNG nho nhung hang tram trieu pixel) lam treo thread upload. */
    private static final long MAX_SOURCE_PIXELS = 100_000_000L;

    private final StorageService storageService;

    @Override
    public String createFor(String storageKey, String contentType) {
        if (!RESIZABLE_TYPES.contains(contentType)) {
            return null;
        }
        try {
            byte[] thumbnail = shrink(storageService.read(storageKey));
            if (thumbnail == null) {
                return null;
            }
            String thumbnailKey = storageKey + THUMBNAIL_SUFFIX;
            storageService.put(thumbnailKey, thumbnail, THUMBNAIL_CONTENT_TYPE);
            return thumbnailKey;
        } catch (IOException | RuntimeException failure) {
            // Co chu dich bo qua: anh hong hoac storage loi chi mat thumbnail, upload van thanh cong.
            log.warn("[Media] cannot create thumbnail for {}: {}", storageKey, failure.getMessage());
            return null;
        }
    }

    /** @return JPEG da thu nho, hoac {@code null} khi anh da nho hon {@link #MAX_EDGE} hoac qua lon de xu ly. */
    private byte[] shrink(byte[] original) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IIOException("Unrecognised image format");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int longestEdge = Math.max(width, height);
                if (longestEdge <= MAX_EDGE || (long) width * height > MAX_SOURCE_PIXELS) {
                    return null;
                }
                return encode(scale(read(reader, longestEdge), width, height, longestEdge));
            } finally {
                reader.dispose();
            }
        }
    }

    /** Doc thua mau (subsampling) de anh lon khong chiem het heap; van giu >= 2x kich thuoc dich cho buoc scale min. */
    private BufferedImage read(ImageReader reader, int longestEdge) throws IOException {
        ImageReadParam param = reader.getDefaultReadParam();
        int step = Math.max(1, longestEdge / (MAX_EDGE * 2));
        param.setSourceSubsampling(step, step, 0, 0);
        return reader.read(0, param);
    }

    private BufferedImage scale(BufferedImage source, int width, int height, int longestEdge) {
        double ratio = (double) MAX_EDGE / longestEdge;
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // JPEG khong co kenh alpha: nen trong suot cua PNG phai ve len nen trang.
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] encode(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpg", output)) {
            throw new IIOException("No JPEG writer available");
        }
        return output.toByteArray();
    }
}
