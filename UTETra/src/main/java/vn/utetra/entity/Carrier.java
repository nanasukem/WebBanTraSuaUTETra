package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Bảng Carriers - đối tác giao hàng của chuỗi (Shipper UTETra, Ahamove...).
 * Phí giao tính theo khoảng cách từ chi nhánh tới địa chỉ khách:
 *   fee = phí cho {@code OrderServiceImpl.BASE_KM} km đầu, mỗi km thêm cộng perKmFee.
 * Khách KHÔNG chọn đối tác: hệ thống tự chọn đối tác đang hoạt động có phí rẻ nhất cho quãng đường đó.
 */
@Entity
@Table(name = "Carriers")
@Getter
@Setter
@NoArgsConstructor
public class Carrier {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "name", nullable = false, unique = true, length = 100)
	private String name;

	@Column(name = "fee", nullable = false, precision = 12, scale = 0)
	private BigDecimal fee = BigDecimal.ZERO; // phí cho các km đầu

	@Column(name = "per_km_fee", nullable = false, precision = 12, scale = 0)
	private BigDecimal perKmFee = BigDecimal.ZERO; // mỗi km thêm

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;
}