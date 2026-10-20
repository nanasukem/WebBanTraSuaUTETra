package vn.utetra.dto.response;

import java.util.List;

/** Trả về mỗi lần trang Checkout tính lại: tiền + danh sách mã */
public record CheckoutPageRes(CheckoutPreviewRes preview, List<VoucherOptionRes> vouchers) {
}