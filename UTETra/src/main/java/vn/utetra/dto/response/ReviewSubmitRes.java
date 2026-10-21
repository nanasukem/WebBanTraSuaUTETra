package vn.utetra.dto.response;

/** Kết quả gửi đánh giá đơn: số ly vừa đánh giá + có đánh giá quán không */
public record ReviewSubmitRes(int reviewedItems, boolean storeReviewed) {
}
