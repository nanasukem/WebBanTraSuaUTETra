package vn.utetra.entity.enums;

/**
 * Trạng thái đơn.
 * Giao tận nơi: PENDING (đơn mới) -> CONFIRMED (quán nhận đơn, đang pha chế) -> READY (pha xong, chờ shipper)
 *               -> PICKED_UP (shipper đã lấy) -> DELIVERING (đang giao) -> DELIVERED (đã giao)
 * Đến lấy     : PENDING -> CONFIRMED (đang pha chế) -> READY (đã xong, chờ khách đến lấy) -> DELIVERED (khách đã nhận)
 * Huỷ: CANCELLED (ai huỷ: Order.cancelledBy). Trả hàng - hoàn tiền: RETURNED.
 */
public enum OrderStatus {
    PENDING, CONFIRMED, READY, PICKED_UP, DELIVERING, DELIVERED, CANCELLED, RETURNED
}
