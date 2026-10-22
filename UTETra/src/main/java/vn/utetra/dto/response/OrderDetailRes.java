package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Chi tiết 1 đơn của khách (giá, mã giảm, địa chỉ đều là bản đã chốt lúc đặt) */
public record OrderDetailRes(
		Integer id,
		String status,
		String statusLabel,
		String badgeClass,
		boolean pickup,
		List<Step> steps,          // thanh tiến trình; rỗng khi đã huỷ / trả hàng
		String branchName,
		String branchAddress,
		String branchPhone,
		String receiverName,
		String receiverPhone,
		String deliveryAddress,
		String deliveryNote,       // ghi chú địa chỉ
		String carrierName,
		String note,               // ghi chú cho quán
		List<Item> items,
		int totalQty,
		BigDecimal itemsTotal,
		BigDecimal productDiscount,
		String productVoucherCode,
		BigDecimal memberDiscount,
		String memberTierName,
		BigDecimal shippingFee,
		BigDecimal shippingDiscount,
		String shippingVoucherCode,
		BigDecimal totalAmount,
		String paymentMethodLabel,
		String paymentStatusLabel,
		String cancelReason,
		String createdAt,
		String deliveredAt,
		boolean canCancel,
		String cancelledBy,        // CUSTOMER / STORE / SYSTEM (null = chưa huỷ)
		PaymentInfoRes payment) {  // đã thanh toán / chưa (COD) / chờ thanh toán / hoàn tiền

	/** 1 dòng món: "2× Cà phê muối · Size L · 70% đường · 50% đá + Kem cheese" */
	public record Item(
			String productName,
			String size,
			String sugarIce,
			String toppings,
			int quantity,
			BigDecimal unitPrice,
			BigDecimal subtotal,
			String note) {         // ghi chú món
	}

	/** 1 bước trên thanh tiến trình */
	public record Step(String label, boolean done, boolean current) {
	}
}