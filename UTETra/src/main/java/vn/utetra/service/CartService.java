package vn.utetra.service;

import vn.utetra.dto.request.CartAddReq;
import vn.utetra.dto.response.CartRes;
import vn.utetra.dto.response.ProductOptionsRes;
import vn.utetra.dto.response.ReorderRes;

public interface CartService {

	CartRes getCart(Integer userId);

	CartRes addItem(Integer userId, CartAddReq req);

	CartRes updateQuantity(Integer userId, Integer cartItemId, Integer quantity);

	CartRes removeItem(Integer userId, Integer cartItemId);

	CartRes updateOptions(Integer userId, Integer cartItemId, CartAddReq req);

	void clear(Integer userId);
	
	ProductOptionsRes getOptions(Integer productId);

	ProductOptionsRes getOptions(Integer productId, Integer branchId);

	ReorderRes reorder(Integer userId, Integer orderId);
}
