package vn.utetra.entity;

import vn.utetra.entity.enums.BranchStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "Branches")
@Getter
@Setter
@NoArgsConstructor
public class Branch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "address", nullable = false, length = 255)
	private String address;
	
	@Column(name = "latitude", precision = 9, scale = 6)
	private BigDecimal latitude;

	@Column(name = "longitude", precision = 9, scale = 6)
	private BigDecimal longitude;

	@Column(name = "phone", length = 15)
	private String phone;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "image_cloudinary_id")
	private String imageCloudinaryId;

	@Column(name = "open_time", nullable = false)
	private LocalTime openTime = LocalTime.of(7, 0);

	@Column(name = "close_time", nullable = false)
	private LocalTime closeTime = LocalTime.of(22, 0);

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private BranchStatus status = BranchStatus.APPROVED;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public boolean isOpenAt(LocalTime t) {
		LocalTime open = openTime == null ? LocalTime.of(7, 0) : openTime;
		LocalTime close = closeTime == null ? LocalTime.of(22, 0) : closeTime;
		if (open.equals(close)) return true;
		return open.isBefore(close) ? !t.isBefore(open) && t.isBefore(close) : !t.isBefore(open) || t.isBefore(close);
	}

	public String getHoursLabel() {
		if (openTime != null && openTime.equals(closeTime)) return "Mở cả ngày";
		return hm(openTime) + " - " + hm(closeTime);
	}

	public boolean isOpenAllDay() {
		return openTime != null && openTime.equals(closeTime);
	}

	public LocalTime lastOrderTime(int minutesBeforeClose) {
		LocalTime close = closeTime == null ? LocalTime.of(22, 0) : closeTime;
		return close.minusMinutes(Math.max(0, minutesBeforeClose));
	}

	public boolean acceptsOrdersAt(LocalTime t, int minutesBeforeClose) {
		if (isOpenAllDay()) return true;
		if (!isOpenAt(t)) return false;
		LocalTime close = closeTime == null ? LocalTime.of(22, 0) : closeTime;
		int secondsLeft = Math.floorMod(close.toSecondOfDay() - t.toSecondOfDay(), 86400);
		return secondsLeft > Math.max(0, minutesBeforeClose) * 60;
	}

	public String openStatusAt(LocalTime t, int minutesBeforeClose) {
		if (isOpenAllDay()) return "Mở cả ngày";
		if (acceptsOrdersAt(t, minutesBeforeClose))
			return "Đang mở cửa · nhận đơn đến " + hm(lastOrderTime(minutesBeforeClose));
		if (isOpenAt(t))
			return "Sắp đóng cửa · đã ngừng nhận đơn từ " + hm(lastOrderTime(minutesBeforeClose));
		return "Đã đóng cửa · mở lại lúc " + hm(openTime);
	}

	private static String hm(LocalTime t) {
		return t == null ? "" : t.getHour() + ":" + String.format("%02d", t.getMinute());
	}

	@PrePersist
	void prePersist() {
		if (createdAt == null)
			createdAt = LocalDateTime.now();
	}
}