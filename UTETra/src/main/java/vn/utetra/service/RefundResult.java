package vn.utetra.service;

/** Kết quả gọi API hoàn tiền: success = cổng đã nhận lệnh hoàn; message = lý do khi lỗi */
public record RefundResult(boolean success, String code, String transactionNo, String message) {
}
