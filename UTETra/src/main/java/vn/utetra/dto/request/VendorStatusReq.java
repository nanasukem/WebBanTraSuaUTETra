package vn.utetra.dto.request;

import vn.utetra.entity.enums.OrderStatus;

/** Quán bấm đổi trạng thái: {"status": "CONFIRMED"} hoặc {"status": "CANCELLED", "reason": "..."} */
public record VendorStatusReq(OrderStatus status, String reason) {
}