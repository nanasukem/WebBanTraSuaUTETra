package vn.utetra.service;

public record RefundResult(boolean success, String code, String transactionNo, String message) {
}
