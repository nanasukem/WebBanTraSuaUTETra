package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Branch {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(columnDefinition = "NVARCHAR(MAX)")
	private String address;

	@Column(length = 15)
	private String phone;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;
}