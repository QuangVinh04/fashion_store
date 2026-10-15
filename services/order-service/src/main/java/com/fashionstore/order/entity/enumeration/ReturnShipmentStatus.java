package com.fashionstore.order.entity.enumeration;

/** Trạng thái vận đơn chiều về, suy ra từ trạng thái GHN. */
public enum ReturnShipmentStatus {
    /** Đã tạo đơn bên GHN, chờ shipper lấy hàng tại khách. */
    READY_TO_PICK,
    /** GHN đã lấy hàng, đang trên đường về kho. */
    PICKED,
    /** GHN báo đã giao tới kho (admin vẫn phải bấm "đã nhận hàng" + QC). */
    ARRIVED,
    /** Đơn trả đã hủy bên GHN trước khi lấy hàng. */
    CANCELLED,
    /** GHN báo giao thất bại / hoàn / thất lạc / hư hỏng: cần admin xử lý, không tự đóng phiếu. */
    NEEDS_ATTENTION
}
