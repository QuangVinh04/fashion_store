package com.fashionstore.catalog.media.service;

public interface ThumbnailService {

    /**
     * Tao ban thu nho cua anh da upload va luu canh ban goc.
     *
     * @return storage key cua thumbnail, hoac {@code null} khi khong tao (dinh dang khong ho tro,
     *         anh da du nho, anh hong, storage loi). Khong bao gio nem loi: thumbnail chi la toi uu,
     *         khong duoc lam hong viec upload.
     */
    String createFor(String storageKey, String contentType);
}
