package vn.utetra.entity;

import vn.utetra.entity.enums.BranchStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Bảng Branches = Chi nhánh. Chuỗi tự mở: Admin mở chi nhánh và chỉ định 1 Manager
 * quản lý. Trạng thái: APPROVED (đang hoạt động) <-> SUSPENDED (tạm ngưng).
 */
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
	private User owner; // Manager được giao quản lý chi nhánh (tên cột owner_id giữ nguyên)

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "address", nullable = false, length = 255)
	private String address;
	
	@Column(name = "latitude", precision = 9, scale = 6)
	private BigDecimal latitude; // vĩ độ, VD 10.850700 (null = chưa nhập)

	@Column(name = "longitude", precision = 9, scale = 6)
	private BigDecimal longitude; // kinh độ, VD 106.771900

	@Column(name = "phone", length = 15)
	private String phone;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "image_cloudinary_id")
	private String imageCloudinaryId;


	/** Giờ mở cửa (mặc định 7:00 - 22:00). Mở = đóng (vd 00:00 - 00:00) nghĩa là mở cả ngày */
	@Column(name = "open_time", nullable = false)
	private LocalTime openTime = LocalTime.of(7, 0);

	@Column(name = "close_time", nullable = false)
	private LocalTime closeTime = LocalTime.of(22, 0);

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private BranchStatus status = BranchStatus.APPROVED;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	/** Chi nhánh có đang mở cửa lúc t không (hỗ trợ cả giờ qua đêm, vd 18:00 - 02:00) */
	public boolean isOpenAt(LocalTime t) {
		LocalTime open = openTime == null ? LocalTime.of(7, 0) : openTime;
		LocalTime close = closeTime == null ? LocalTime.of(22, 0) : closeTime;
		if (open.equals(close)) return true; // mở cả ngày
		return open.isBefore(close) ? !t.isBefore(open) && t.isBefore(close) : !t.isBefore(open) || t.isBefore(close);
	}

	/** "7:00 - 22:00" / "Mở cả ngày" */
	public String getHoursLabel() {
		if (openTime != null && openTime.equals(closeTime)) return "Mở cả ngày";
		return hm(openTime) + " - " + hm(closeTime);
	}

	/** "Đang mở cửa · đóng lúc 22:00" / "Đã đóng cửa · mở lại lúc 7:00" */
	public String openStatusAt(LocalTime t) {
		if (openTime != null && openTime.equals(closeTime)) return "Mở cả ngày";
		return isOpenAt(t) ? "Đang mở cửa · đóng lúc " + hm(closeTime) : "Đã đóng cửa · mở lại lúc " + hm(openTime);
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