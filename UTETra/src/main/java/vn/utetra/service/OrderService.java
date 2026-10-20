package vn.utetra.service;

import vn.utetra.dto.request.CheckoutReq;
import vn.utetra.dto.response.CheckoutPreviewRes;
import vn.utetra.dto.response.OrderSummaryRes;
import vn.utetra.dto.response.VoucherOptionRes;
import vn.utetra.entity.Order;

import java.util.List;

public interface OrderService {

	/** Tính tiền để hiện ở trang Checkout, KHÔNG lưu đơn */
	CheckoutPreviewRes preview(Integer userId, CheckoutReq req);

	/** Đặt hàng: kiểm tra lại toàn bộ, lưu đơn, xoá giỏ, ghi nhận voucher */
	Order placeOrder(Integer userId, CheckoutReq req);

	/** Khung "Mã của bạn": các mã khách thấy được + dùng được không + lý do + số tiền giảm */
	List<VoucherOptionRes> voucherOptions(Integer userId, CheckoutReq req);

	/** Tóm tắt 1 đơn của chính user (trang Đặt hàng thành công) */
	OrderSummaryRes getOrderSummary(Integer userId, Integer orderId);
}