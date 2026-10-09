package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Bảng Carriers - nhà vận chuyển */
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
	private BigDecimal fee = BigDecimal.ZERO;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;
}