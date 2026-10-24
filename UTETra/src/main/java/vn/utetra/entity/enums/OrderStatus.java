package vn.utetra.entity.enums;

public enum OrderStatus {
    AWAITING_PAYMENT, PENDING, CONFIRMED, READY, PICKED_UP, DELIVERING, DELIVERED, CANCELLED, RETURNED;

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
