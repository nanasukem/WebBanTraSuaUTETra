package vn.utetra.entity.enums;

/** Kết quả xử lý báo cáo vấn đề với đơn */
public enum IssueStatus {
    PENDING("Đang chờ quán xử lý"),
    REFUNDED("Quán đã hoàn tiền"),
    REMADE("Quán làm lại món"),
    REJECTED("Quán từ chối hoàn tiền");

    private final String label;

    IssueStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
