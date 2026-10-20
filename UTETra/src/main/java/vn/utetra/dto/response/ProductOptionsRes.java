package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Tùy chọn của 1 món để vẽ modal: giá cộng thêm theo size + danh sách topping đang bán */
public record ProductOptionsRes(Map<String, BigDecimal> sizes, List<CartItemRes.ToppingRes> toppings) {
}