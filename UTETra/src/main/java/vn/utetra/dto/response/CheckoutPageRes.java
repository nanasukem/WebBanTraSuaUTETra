package vn.utetra.dto.response;

import java.util.List;

public record CheckoutPageRes(CheckoutPreviewRes preview, List<VoucherOptionRes> vouchers) {
}