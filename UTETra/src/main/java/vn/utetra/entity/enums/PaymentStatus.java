package vn.utetra.entity.enums;

/**
 * Trạng thái thanh toán của đơn.
 * UNPAID: chưa trả (COD chưa giao / VNPAY-MoMo chưa trả xong)
 * PAID: đã trả (online trả trước, hoặc COD đã thu tiền khi giao)
 * REFUND_PENDING: đơn đã trả trước bị huỷ, đang hoàn tiền qua VNPAY / MoMo
 * REFUNDED: đã hoàn tiền xong
 */
public enum PaymentStatus {
    UNPAID, PAID, REFUND_PENDING, REFUNDED
}
