package vn.utetra.entity.enums;

public enum BranchStatus {
    APPROVED("Đang hoạt động"),
    SUSPENDED("Tạm ngưng");

    private final String label;

    BranchStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
