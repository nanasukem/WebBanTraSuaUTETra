package vn.utetra.dto.response;

/** 1 thông báo trên chuông (type: ORDER, REVIEW, COIN, VOUCHER, USER) */
public record NotificationRes(Integer id, String type, String title, String content, String link, String time,
		boolean read) {
}
