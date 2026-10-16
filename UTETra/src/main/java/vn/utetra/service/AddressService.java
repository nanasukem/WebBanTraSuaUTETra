package vn.utetra.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.entity.Address;
import vn.utetra.entity.User;
import vn.utetra.repository.AddressRepository;
import vn.utetra.repository.UserRepository;

import java.util.List;

@Service
public class AddressService {

	private final AddressRepository addressRepository;
	private final UserRepository userRepository;

	public AddressService(AddressRepository addressRepository, UserRepository userRepository) {
		this.addressRepository = addressRepository;
		this.userRepository = userRepository;
	}

	public List<Address> getUserAddresses(Integer userId) {
		return addressRepository.findByUserIdOrderByIsDefaultDesc(userId);
	}

	@Transactional
	public Address addAddress(Integer userId, Address address) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));

		if (Boolean.TRUE.equals(address.getIsDefault())) {
			addressRepository.resetDefaultAddressByUserId(userId);
		} else if (addressRepository.findByUserIdOrderByIsDefaultDesc(userId).isEmpty()) {
			address.setIsDefault(true);
		}

		address.setUser(user);
		return addressRepository.save(address);
	}

	@Transactional
	public void setDefaultAddress(Integer userId, Integer addressId) {
		addressRepository.resetDefaultAddressByUserId(userId);
		Address address = addressRepository.findById(addressId)
				.orElseThrow(() -> new IllegalArgumentException("Địa chỉ không tồn tại"));
		if (!address.getUser().getId().equals(userId)) {
			throw new SecurityException("Không có quyền chỉnh sửa địa chỉ này");
		}
		address.setIsDefault(true);
		addressRepository.save(address);
	}

	@Transactional
	public void deleteAddress(Integer userId, Integer addressId) {
		Address address = addressRepository.findById(addressId)
				.orElseThrow(() -> new IllegalArgumentException("Địa chỉ không tồn tại"));
		if (!address.getUser().getId().equals(userId)) {
			throw new SecurityException("Không có quyền thực hiện thao tác này");
		}
		addressRepository.delete(address);
	}
}