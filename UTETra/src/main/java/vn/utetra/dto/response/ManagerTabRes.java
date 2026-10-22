package vn.utetra.dto.response;

/** 1 tab trên trang "Đơn hàng của chi nhánh": status = mã tab (PENDING, SHIPPING...), count = số đơn */
public record ManagerTabRes(String status, String label, long count) {
}