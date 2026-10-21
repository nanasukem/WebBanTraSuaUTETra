package vn.utetra.service;

import vn.utetra.dto.request.CartAddReq;
import vn.utetra.dto.response.CartRes;
import vn.utetra.dto.response.ProductOptionsRes;
import vn.utetra.dto.response.ReorderRes;

public interface CartService {

	/** Giỏ hàng của user (giá tính lại từ DB) */
	CartRes getCart(Integer userId);

	/** Thêm món. Trùng cấu hình (món, size, đường, đá, bộ topping) thì cộng dồn số lượng */
	CartRes addItem(Integer userId, CartAddReq req);

	/** Sửa số lượng 1 dòng (1-50). Chỉ sửa được dòng của chính mình */
	CartRes updateQuantity(Integer userId, Integer cartItemId, Integer quantity);

	/** Xoá 1 dòng. Chỉ xoá được dòng của chính mình */
	CartRes removeItem(Integer userId, Integer cartItemId);

	/** Xoá hết giỏ */
	void clear(Integer userId);
	
	/** Size (giá cộng thêm) + topping đang bán của 1 món - dùng cho modal chọn món */
	ProductOptionsRes getOptions(Integer productId);

	/** "Mua lại": cho lại các món của 1 đơn cũ (của chính mình) vào giỏ, giữ size / đường / đá / topping / ghi chú */
	ReorderRes reorder(Integer userId, Integer orderId);
}
