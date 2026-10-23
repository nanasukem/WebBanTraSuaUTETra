package vn.utetra.entity.enums;

/** Hình thức nhận hàng */
public enum DeliveryType {
    DELIVERY, // giao tận nơi (Shipper UTETra giao, có phí ship)
    PICKUP    // khách đến chi nhánh lấy (không phí ship)
}