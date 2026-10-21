package vn.utetra.entity.enums;

/**
 * Trạng thái chi nhánh (chuỗi tự mở: Admin mở chi nhánh, không có bước đăng ký/duyệt).
 * Admin bật/tắt: Đang hoạt động <-> Tạm ngưng.
 * Giữ tên APPROVED để không phải đổi dữ liệu trong database.
 */
public enum BranchStatus {
    APPROVED("Đang hoạt động"),  // khách thấy và đặt hàng được
    SUSPENDED("Tạm ngưng");      // ẩn với khách, không nhận đơn mới

    private final String label;

    BranchStatus(String label) {
        this.label = label;
    }

    /** Chữ hiển thị tiếng Việt, dùng trong Thymeleaf: ${b.status.label} */
    public String getLabel() {
        return label;
    }
}
