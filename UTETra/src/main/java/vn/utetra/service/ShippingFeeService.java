package vn.utetra.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.entity.Carrier;
import vn.utetra.exception.BusinessException;
import vn.utetra.repository.CarrierRepository;

@Service
@Transactional(readOnly = true)
public class ShippingFeeService {

	public static final String SHIPPER_NAME = "Shipper UTETra";
	public static final int BASE_KM = 3;
	public static final BigDecimal DEFAULT_BASE_FEE = BigDecimal.valueOf(15000);
	public static final BigDecimal DEFAULT_PER_KM_FEE = BigDecimal.valueOf(10000);
	private static final BigDecimal MAX_FEE = BigDecimal.valueOf(100_000);

	private final CarrierRepository carrierRepository;

	public ShippingFeeService(CarrierRepository carrierRepository) {
		this.carrierRepository = carrierRepository;
	}

	public Carrier shipper() {
		return carrierRepository.findByNameContainingIgnoreCaseOrderByIdAsc(SHIPPER_NAME).stream().findFirst()
				.orElseGet(() -> carrierRepository.findAll().stream().findFirst().orElse(null));
	}

	public BigDecimal baseFee() {
		Carrier c = shipper();
		return c == null || c.getFee() == null ? DEFAULT_BASE_FEE : c.getFee();
	}

	public BigDecimal perKmFee() {
		Carrier c = shipper();
		return c == null || c.getPerKmFee() == null ? DEFAULT_PER_KM_FEE : c.getPerKmFee();
	}

	public BigDecimal feeFor(BigDecimal distanceKm) {
		double diffKm = distanceKm.subtract(BigDecimal.valueOf(BASE_KM)).doubleValue();
		int extraKm = (int) Math.max(0, Math.ceil(diffKm));
		return baseFee().add(perKmFee().multiply(BigDecimal.valueOf(extraKm)));
	}

	@Transactional
	public Carrier update(BigDecimal baseFee, BigDecimal perKmFee) {
		check(baseFee, "Phí " + BASE_KM + " km đầu");
		check(perKmFee, "Phí mỗi km thêm");
		Carrier c = shipper();
		if (c == null) {
			c = new Carrier();
			c.setName(SHIPPER_NAME);
			c.setIsActive(true);
		}
		c.setFee(baseFee);
		c.setPerKmFee(perKmFee);
		return carrierRepository.save(c);
	}

	private static void check(BigDecimal v, String label) {
		if (v == null) throw new BusinessException("Vui lòng nhập " + label.toLowerCase());
		if (v.signum() < 0 || v.compareTo(MAX_FEE) > 0) throw new BusinessException(label + " từ 0đ đến 100.000đ");
		if (v.stripTrailingZeros().scale() > 0) throw new BusinessException(label + " phải là số nguyên (đồng)");
	}
}
