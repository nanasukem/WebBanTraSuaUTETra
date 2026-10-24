package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CartRes(List<CartItemRes> items, int totalQty, BigDecimal subtotal) {
}