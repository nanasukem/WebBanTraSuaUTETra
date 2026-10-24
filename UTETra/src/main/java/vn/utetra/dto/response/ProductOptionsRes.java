package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductOptionsRes(Map<String, BigDecimal> sizes, List<CartItemRes.ToppingRes> toppings,
		boolean toppingItem,
		List<CartItemRes.ToppingRes> stoppedToppings,
		List<CartItemRes.ToppingRes> outToppings,
		String branchName) {
}