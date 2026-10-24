package vn.utetra.dto.response;

public record NotificationRes(Integer id, String type, String title, String content, String link, String time,
		boolean read) {
}
