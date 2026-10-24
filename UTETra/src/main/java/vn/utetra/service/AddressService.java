package vn.utetra.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.request.AddressReq;
import vn.utetra.dto.response.AddressRes;
import vn.utetra.entity.Address;
import vn.utetra.entity.User;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.AddressRepository;
import vn.utetra.repository.UserRepository;

import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AddressService {

	private static final String PHONE_REGEX = "0\\d{9}";
	private static final Set<String> ADDRESS_TYPES = Set.of("HOME", "OFFICE");

	private final AddressRepository addressRepository;
	private final UserRepository userRepository;

	public AddressService(AddressRepository addressRepository, UserRepository userRepository) {
		this.addressRepository = addressRepository;
		this.userRepository = userRepository;
	}

	public List<AddressRes> list(Integer userId) {
		return addressRepository.findByUserIdOrderByIsDefaultDesc(userId).stream().map(this::toRes).toList();
	}

	@Transactional
	public AddressRes add(Integer userId, AddressReq req) {
		validate(req);
		List<Address> existing = addressRepository.findByUserIdOrderByIsDefaultDesc(userId);
		boolean makeDefault = existing.isEmpty() || Boolean.TRUE.equals(req.getIsDefault());
		if (makeDefault) existing.forEach(a -> a.setIsDefault(false));

		Address a = new Address();
		a.setUser(userRepository.getReferenceById(userId));
		apply(a, req);
		a.setIsDefault(makeDefault);
		return toRes(addressRepository.save(a));
	}

	@Transactional
	public AddressRes update(Integer userId, Integer addressId, AddressReq req) {
		validate(req);
		Address a = findOwned(userId, addressId);
		apply(a, req);
		if (Boolean.TRUE.equals(req.getIsDefault()) && !Boolean.TRUE.equals(a.getIsDefault())) {
			addressRepository.findByUserIdOrderByIsDefaultDesc(userId).forEach(x -> x.setIsDefault(false));
			a.setIsDefault(true);
		}
		return toRes(a);
	}

	@Transactional
	public void setDefault(Integer userId, Integer addressId) {
		Address target = findOwned(userId, addressId);
		addressRepository.findByUserIdOrderByIsDefaultDesc(userId).forEach(a -> a.setIsDefault(false));
		target.setIsDefault(true);
	}

	@Transactional
	public void delete(Integer userId, Integer addressId) {
		removeAndKeepDefault(findOwned(userId, addressId), userId);
	}

	public List<Address> getUserAddresses(Integer userId) {
		return addressRepository.findByUserIdOrderByIsDefaultDesc(userId);
	}

	@Transactional
	public Address addAddress(Integer userId, Address address) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));
		List<Address> existing = addressRepository.findByUserIdOrderByIsDefaultDesc(userId);
		boolean makeDefault = existing.isEmpty() || Boolean.TRUE.equals(address.getIsDefault());
		if (makeDefault) existing.forEach(a -> a.setIsDefault(false));
		address.setIsDefault(makeDefault);
		address.setUser(user);
		return addressRepository.save(address);
	}

	@Transactional
	public void setDefaultAddress(Integer userId, Integer addressId) {
		Address target = addressRepository.findById(addressId)
				.orElseThrow(() -> new IllegalArgumentException("Địa chỉ không tồn tại"));
		if (!target.getUser().getId().equals(userId)) {
			throw new SecurityException("Không có quyền chỉnh sửa địa chỉ này");
		}
		addressRepository.findByUserIdOrderByIsDefaultDesc(userId).forEach(a -> a.setIsDefault(false));
		target.setIsDefault(true);
	}

	@Transactional
	public void deleteAddress(Integer userId, Integer addressId) {
		Address address = addressRepository.findById(addressId)
				.orElseThrow(() -> new IllegalArgumentException("Địa chỉ không tồn tại"));
		if (!address.getUser().getId().equals(userId)) {
			throw new SecurityException("Không có quyền thực hiện thao tác này");
		}
		removeAndKeepDefault(address, userId);
	}

	private void apply(Address a, AddressReq req) {
		String street = trimToNull(req.getStreet());
		String ward = trimToNull(req.getWard());
		String province = trimToNull(req.getProvince());
		a.setReceiverName(req.getReceiverName().trim());
		a.setReceiverPhone(req.getReceiverPhone().trim());
		a.setStreet(street);
		a.setWard(ward);
		a.setProvince(province);
		a.setAddressLine(composeLine(req));
		a.setLatitude(req.getLatitude());
		a.setLongitude(req.getLongitude());
		a.setAddressType(trimToNull(req.getAddressType()) == null ? null : req.getAddressType().trim().toUpperCase());
		a.setNote(trimToNull(req.getNote()));
	}

	private String composeLine(AddressReq req) {
		if (hasParts(req)) {
			return req.getStreet().trim() + ", " + req.getWard().trim() + ", " + req.getProvince().trim();
		}
		return req.getAddressLine().trim();
	}

	private boolean hasParts(AddressReq req) {
		return !isBlank(req.getStreet()) || !isBlank(req.getWard()) || !isBlank(req.getProvince());
	}

	private void removeAndKeepDefault(Address target, Integer userId) {
		boolean wasDefault = Boolean.TRUE.equals(target.getIsDefault());
		Integer removedId = target.getId();
		addressRepository.delete(target);
		if (wasDefault) {
			addressRepository.findByUserIdOrderByIsDefaultDesc(userId).stream()
					.filter(a -> !a.getId().equals(removedId)).findFirst()
					.ifPresent(a -> a.setIsDefault(true));
		}
	}

	private Address findOwned(Integer userId, Integer addressId) {
		return addressRepository.findById(addressId)
				.filter(a -> a.getUser().getId().equals(userId))
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ #" + addressId));
	}

	private void validate(AddressReq req) {
		if (isBlank(req.getReceiverName())) throw new BusinessException("Vui lòng nhập họ và tên người nhận");
		if (req.getReceiverPhone() == null || !req.getReceiverPhone().trim().matches(PHONE_REGEX))
			throw new BusinessException("Số điện thoại phải có 10 số, bắt đầu bằng 0");

		if (hasParts(req)) {
			if (isBlank(req.getProvince())) throw new BusinessException("Vui lòng chọn Tỉnh/Thành phố");
			if (isBlank(req.getWard())) throw new BusinessException("Vui lòng chọn Phường/Xã");
			if (isBlank(req.getStreet())) throw new BusinessException("Vui lòng nhập Tên đường, Toà nhà, Số nhà");
		} else if (isBlank(req.getAddressLine())) {
			throw new BusinessException("Vui lòng nhập địa chỉ");
		}
		if (composeLine(req).length() > 255) throw new BusinessException("Địa chỉ quá dài (tối đa 255 ký tự)");
		if (req.getNote() != null && req.getNote().trim().length() > 200)
			throw new BusinessException("Ghi chú địa chỉ tối đa 200 ký tự");

		if ((req.getLatitude() == null) != (req.getLongitude() == null))
			throw new BusinessException("Vị trí ghim trên bản đồ không hợp lệ");
		String type = trimToNull(req.getAddressType());
		if (type != null && !ADDRESS_TYPES.contains(type.toUpperCase()))
			throw new BusinessException("Loại địa chỉ chỉ được là Nhà riêng hoặc Văn phòng");
	}

	private boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private String trimToNull(String s) {
		return isBlank(s) ? null : s.trim();
	}

	private AddressRes toRes(Address a) {
		return new AddressRes(a.getId(), a.getReceiverName(), a.getReceiverPhone(), a.getAddressLine(),
				a.getProvince(), a.getWard(), a.getStreet(), a.getAddressType(), a.getNote(),
				a.getLatitude(), a.getLongitude(), Boolean.TRUE.equals(a.getIsDefault()));
	}
}