package vn.utetra.dto.response;

import java.util.List;

/** Kết quả "Mua lại": số ly đã cho vào giỏ + các món / topping không thêm được (ngừng bán, hết...) */
public record ReorderRes(int addedQty, List<String> notes) {
}
