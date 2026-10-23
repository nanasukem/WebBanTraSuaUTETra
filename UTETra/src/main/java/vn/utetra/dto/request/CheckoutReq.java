package vn.utetra.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.PaymentMethod;

/** Dữ liệu trang Checkout gửi lên (xem trước + đặt hàng). Mọi kiểm tra nằm ở OrderServiceImpl */
@Getter
@Setter
@NoArgsConstructor
public class CheckoutReq {

	private Integer branchId;

	/** DELIVERY = giao tận nơi, PICKUP = đến chi nhánh lấy */
	private DeliveryType deliveryType = DeliveryType.DELIVERY;

	// ----- Giao tận nơi -----
	private Integer addressId;
	private Integer carrierId;

	// ----- Đến lấy: người nhận (trang Checkout điền sẵn từ hồ sơ) -----
	@Size(max = 100, message = "Tên người nhận tối đa 100 ký tự")
	private String receiverName;

	@Size(max = 15, message = "Số điện thoại tối đa 15 ký tự")
	private String receiverPhone;

	// ----- Mã giảm giá -----
	private String productVoucherCode;

	private PaymentMethod paymentMethod = PaymentMethod.COD;

	@Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
	private String note;
}