package vn.utetra.dto.response;

/** 1 tab trên trang "Đơn hàng của shop": status = tên trạng thái, count = số đơn */
public record ManagerTabRes(String status, String label, long count) {
}