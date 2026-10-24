package vn.utetra.dto.request;

import vn.utetra.entity.enums.OrderStatus;

public record ManagerStatusReq(OrderStatus status, String reason) {
}