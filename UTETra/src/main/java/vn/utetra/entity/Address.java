package vn.utetra.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Address {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "receiver_name", nullable = false, length = 100)
	private String receiverName;

	@Column(name = "receiver_phone", nullable = false, length = 15)
	private String receiverPhone;

	@Column(name = "address_line", nullable = false, length = 255)
	private String addressLine;

	@Column(name = "latitude", precision = 9, scale = 6)
	private BigDecimal latitude;

	@Column(name = "longitude", precision = 9, scale = 6)
	private BigDecimal longitude;

	@Column(name = "province", length = 100)
	private String province;

	@Column(name = "ward", length = 100)
	private String ward;

	@Column(name = "street", length = 150)
	private String street;

	@Column(name = "address_type", length = 10)
	private String addressType;
	
	@Column(name = "note", length = 200)
	private String note;
	
	@Column(name = "is_default", nullable = false)
	private Boolean isDefault = false;
}