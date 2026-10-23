package vn.utetra.entity.enums;

/**
 * Trạng thái đơn.
 * Trả trước (VNPAY / MoMo): AWAITING_PAYMENT (chờ thanh toán, quán CHƯA thấy đơn) -> trả xong -> PENDING
 *                          quá 15 phút chưa trả -> CANCELLED (hệ thống tự huỷ)
 * Giao tận nơi: PENDING (đơn mới) -> CONFIRMED (quán nhận đơn, đang pha chế) -> READY (pha xong, chờ shipper)
 *               -> PICKED_UP (shipper đã lấy) -> DELIVERING (đang giao) -> DELIVERED (đã giao)
 * Đến lấy     : PENDING -> CONFIRMED (đang pha chế) -> READY (đã xong, chờ khách đến lấy) -> DELIVERED (khách đã nhận)
 * Huỷ: CANCELLED (ai huỷ: Order.cancelledBy). Trả hàng - hoàn tiền: RETURNED.
 */
public enum OrderStatus {
    AWAITING_PAYMENT, PENDING, CONFIRMED, READY, PICKED_UP, DELIVERING, DELIVERED, CANCELLED, RETURNED;

    /** Câu dễ đọc cho thông báo / nhật ký hoạt động: "Xác nhận đơn #12", "Huỷ đơn #12 (Hết nguyên liệu)" */
    public static String actionText(OrderStatus to, Integer orderId, String reason) {
        String don = "đơn #" + orderId;
        String text = to == null ? "Cập nhật " + don : switch (to) {
            case CONFIRMED -> "Xác nhận " + don + " (bắt đầu pha chế)";
            case READY -> "Pha xong " + don;
            case PICKED_UP -> "Shipper đã lấy hàng " + don;
            case DELIVERING -> "Đang giao " + don;
            case DELIVERED -> "Đã giao xong " + don;
            case CANCELLED -> "Huỷ " + don;
            default -> "Cập nhật " + don + " sang " + to.name();
        };
        return reason == null || reason.isBlank() ? text : text + " (" + reason.trim() + ")";
    }
}
