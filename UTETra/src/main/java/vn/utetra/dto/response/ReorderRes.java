package vn.utetra.dto.response;

import java.util.List;

public record ReorderRes(int addedQty, List<String> notes) {
}
