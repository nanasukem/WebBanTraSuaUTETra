package vn.utetra.service;

import vn.utetra.dto.request.CheckoutReq;
import vn.utetra.dto.response.CheckoutPreviewRes;
import vn.utetra.dto.response.OrderSummaryRes;
import vn.utetra.dto.response.VoucherOptionRes;
import vn.utetra.entity.Order;

import java.util.List;

public interface OrderService {

	CheckoutPreviewRes preview(Integer userId, CheckoutReq req);

	Order placeOrder(Integer userId, CheckoutReq req);

	List<VoucherOptionRes> voucherOptions(Integer userId, CheckoutReq req);

	OrderSummaryRes getOrderSummary(Integer userId, Integer orderId);
}