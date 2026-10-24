package vn.utetra.entity.enums;

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
