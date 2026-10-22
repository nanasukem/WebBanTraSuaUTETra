package vn.utetra.entity.enums;

/** Hình thức nhận hàng */
public enum DeliveryType {
    DELIVERY, // giao tận nơi (có đối tác giao hàng, phí ship)
    PICKUP    // khách đến chi nhánh lấy (không phí ship)
}