package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "Toppings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Topping {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false)
	private BigDecimal price;

	@Column(name = "is_available", nullable = false)
	private Boolean isAvailable = true;
}