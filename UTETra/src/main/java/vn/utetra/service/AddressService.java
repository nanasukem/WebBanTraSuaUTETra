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

/**
 * Sổ địa chỉ nhận hàng.
 * - Hàm mới (list, add, update, setDefault, delete): trả DTO, dùng cho trang Checkout và /api/addresses.
 *   Địa chỉ kiểu Shopee: Tỉnh/Thành phố + Phường/Xã (sau sáp nhập 2025) + Tên đường/Toà nhà/Số nhà,
 *   server tự ghép thành address_line (đơn hàng chép dòng này lúc đặt).
 * - Hàm cũ (getUserAddresses, addAddress, setDefaultAddress, deleteAddress): giữ nguyên tên và kiểu lỗi
 *   để các trang đang gọi không bị ảnh hưởng.
 */
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

	// ================= HÀM MỚI (trả DTO) =================

	/** Địa chỉ của user, mặc định lên đầu */
	public List<AddressRes> list(Integer userId) {
		return addressRepository.findByUserIdOrderByIsDefaultDesc(userId).stream().map(this::toRes).toList();
	}

	/** Thêm địa chỉ. Địa chỉ đầu tiên tự thành mặc định */
	@Transactional
	public AddressRes add(Integer userId, AddressReq req) {
		validate(req);
		List<Address> existing = addressRepository.findByUserIdOrderByIsDefaultDesc(userId);
		boolean makeDefault = existing.isEmpty() || Boolean.TRUE.equals(req.getIsDefault());
		if (makeDefault) existing.forEach(a -> a.setIsDefault(false)); // chỉ 1 địa chỉ mặc định

		Address a = new Address();
		a.setUser(userRepository.getReferenceById(userId));
		apply(a, req);
		a.setIsDefault(makeDefault);
		return toRes(addressRepository.save(a));
	}

	/** Sửa địa chỉ của chính mình. Tick mặc định thì bỏ mặc định các địa chỉ khác */
	@Transactional
	public AddressRes update(Integer userId, Integer addressId, AddressReq req) {
		validate(req);
		Address a = findOwned(userId, addressId);
		apply(a, req);
		if (Boolean.TRUE.equals(req.getIsDefault()) && !Boolean.TRUE.equals(a.getIsDefault())) {
			addressRepository.findByUserIdOrderByIsDefaultDesc(userId).forEach(x -> x.setIsDefault(false));
			a.setIsDefault(true);
		}
		// Địa chỉ đang mặc định thì giữ nguyên mặc định (luôn phải có 1 địa chỉ mặc định)
		return toRes(a);
	}

	/** Đặt làm mặc định (bỏ mặc định các địa chỉ khác) */
	@Transactional
	public void setDefault(Integer userId, Integer addressId) {
		Address target = findOwned(userId, addressId);
		addressRepository.findByUserIdOrderByIsDefaultDesc(userId).forEach(a -> a.setIsDefault(false));
		target.setIsDefault(true);
	}

	/** Xoá. Xoá địa chỉ mặc định thì địa chỉ còn lại đầu tiên thành mặc định */
	@Transactional
	public void delete(Integer userId, Integer addressId) {
		removeAndKeepDefault(findOwned(userId, addressId), userId);
	}

	// ================= HÀM CŨ (giữ tên + kiểu lỗi cũ) =================

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
		if (!target.getUser().getId().equals(userId)) { // kiểm tra quyền TRƯỚC khi sửa gì
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

	// ================= HÀM PHỤ =================

	/** Chép dữ liệu form vào entity. Kiểu mới (đủ 3 ô) thì tự ghép address_line */
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

	/** "76 Đường số 1B" + "Phường An Lạc" + "Thành phố Hồ Chí Minh" -> "76 Đường số 1B, Phường An Lạc, Thành phố Hồ Chí Minh" */
	private String composeLine(AddressReq req) {
		if (hasParts(req)) {
			return req.getStreet().trim() + ", " + req.getWard().trim() + ", " + req.getProvince().trim();
		}
		return req.getAddressLine().trim(); // kiểu cũ: 1 dòng địa chỉ
	}

	private boolean hasParts(AddressReq req) {
		return !isBlank(req.getStreet()) || !isBlank(req.getWard()) || !isBlank(req.getProvince());
	}

	private void removeAndKeepDefault(Address target, Integer userId) {
		boolean wasDefault = Boolean.TRUE.equals(target.getIsDefault());
		Integer removedId = target.getId();
		addressRepository.delete(target); // đơn hàng đã chép địa chỉ dạng chữ nên xoá không ảnh hưởng đơn cũ
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

		if (hasParts(req)) { // kiểu mới: phải đủ cả 3 phần
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