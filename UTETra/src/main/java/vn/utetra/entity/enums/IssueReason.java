package vn.utetra.entity.enums;

/** Lý do khách báo vấn đề với đơn đã nhận (giống app chuỗi trà sữa thật) */
public enum IssueReason {
    WRONG_ITEM("Giao sai món / sai size, topping"),
    MISSING_ITEM("Thiếu món"),
    SPILLED("Đổ vỡ, rò rỉ"),
    QUALITY("Món không đúng vị / có dị vật"),
    OTHER("Vấn đề khác");

    private final String label;

    IssueReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
