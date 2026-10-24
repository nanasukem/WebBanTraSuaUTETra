package vn.utetra.service;

public record GatewayResult(boolean verified, String status, String txnRef, String transactionNo, long amount,
		String bankCode, String responseCode, String message) {

	public boolean success() { return "SUCCESS".equals(status); }

	public boolean pending() { return "PENDING".equals(status) || "NOT_FOUND".equals(status); }

	public static GatewayResult invalid(String message) {
		return new GatewayResult(false, "FAILED", null, null, 0, null, "97", message);
	}
}
