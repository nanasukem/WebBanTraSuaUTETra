package vn.utetra.service.impl;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.request.CarrierReq;
import vn.utetra.entity.Carrier;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.CarrierRepository;
import vn.utetra.service.CarrierService;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CarrierServiceImpl implements CarrierService {

	private final CarrierRepository carrierRepository;

	public CarrierServiceImpl(CarrierRepository carrierRepository) {
		this.carrierRepository = carrierRepository;
	}

	@Override
	public List<Carrier> search(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return carrierRepository.findAll(Sort.by("id"));
		}
		return carrierRepository.findByNameContainingIgnoreCaseOrderByIdAsc(keyword.trim());
	}

	@Override
	public List<Carrier> findActive() {
		return carrierRepository.findByIsActiveTrueOrderByFeeAsc();
	}

	@Override
	public Carrier findById(Integer id) {
		return carrierRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đối tác giao hàng #" + id));
	}

	@Override
	@Transactional
	public Carrier create(CarrierReq req) {
		String name = req.getName().trim();
		if (carrierRepository.existsByNameIgnoreCase(name)) {
			throw new BusinessException("Đối tác giao hàng " + name + " đã tồn tại");
		}
		Carrier c = new Carrier();
		c.setName(name);
		c.setFee(req.getFee());
		c.setPerKmFee(req.getPerKmFee() == null ? java.math.BigDecimal.ZERO : req.getPerKmFee());
		c.setIsActive(true);
		return carrierRepository.save(c);
	}

	@Override
	@Transactional
	public Carrier update(Integer id, CarrierReq req) {
		Carrier c = findById(id);
		String name = req.getName().trim();
		if (carrierRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
			throw new BusinessException("Đối tác giao hàng " + name + " đã tồn tại");
		}
		c.setName(name);
		c.setFee(req.getFee()); // đơn cũ đã lưu shipping_fee riêng nên đổi phí không ảnh hưởng đơn cũ
		c.setPerKmFee(req.getPerKmFee() == null ? java.math.BigDecimal.ZERO : req.getPerKmFee());
		return carrierRepository.save(c);
	}

	@Override
	@Transactional
	public void toggleActive(Integer id) {
		Carrier c = findById(id);
		c.setIsActive(!Boolean.TRUE.equals(c.getIsActive()));
	}

	@Override
	public CarrierReq toReq(Carrier c) {
		CarrierReq req = new CarrierReq();
		req.setName(c.getName());
		req.setFee(c.getFee());
		req.setPerKmFee(c.getPerKmFee());
		return req;
	}
}