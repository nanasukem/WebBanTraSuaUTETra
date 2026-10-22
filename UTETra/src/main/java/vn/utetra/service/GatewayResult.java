package vn.utetra.service;

/**
 * Kết quả cổng thanh toán báo về (returnUrl / IPN / truy vấn lại).
 * verified = chữ ký đúng (dữ liệu đúng là của VNPAY / MoMo, không bị sửa trên trình duyệt)
 * status: SUCCESS / PENDING (khách chưa trả xong) / FAILED (lỗi, huỷ, hết hạn...) / NOT_FOUND (cổng chưa có giao dịch)
 */
public record GatewayResult(boolean verified, String status, String txnRef, String transactionNo, long amount,
		String bankCode, String responseCode, String message) {

	public boolean success() { return "SUCCESS".equals(status); }

	public boolean pending() { return "PENDING".equals(status) || "NOT_FOUND".equals(status); }

	public static GatewayResult invalid(String message) {
		return new GatewayResult(false, "FAILED", null, null, 0, null, "97", message);
	}
}
