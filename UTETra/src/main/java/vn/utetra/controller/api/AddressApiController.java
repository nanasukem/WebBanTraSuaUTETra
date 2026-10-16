package vn.utetra.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.utetra.entity.Address;
import vn.utetra.service.AddressService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/addresses")
public class AddressApiController {

	private final AddressService addressService;

	public AddressApiController(AddressService addressService) {
		this.addressService = addressService;
	}

	// GET /api/addresses/user/{userId} (Lấy sổ địa chỉ của user)
	@GetMapping("/user/{userId}")
	public ResponseEntity<List<Address>> getUserAddresses(@PathVariable Integer userId) {
		return ResponseEntity.ok(addressService.getUserAddresses(userId));
	}

	// POST /api/addresses/user/{userId} (Thêm địa chỉ mới)
	@PostMapping("/user/{userId}")
	public ResponseEntity<Address> addAddress(@PathVariable Integer userId, @RequestBody Address address) {
		return ResponseEntity.ok(addressService.addAddress(userId, address));
	}

	// PUT /api/addresses/user/{userId}/default/{addressId} (Đặt mặc định)
	@PutMapping("/user/{userId}/default/{addressId}")
	public ResponseEntity<Map<String, String>> setDefaultAddress(@PathVariable Integer userId,
			@PathVariable Integer addressId) {
		addressService.setDefaultAddress(userId, addressId);
		return ResponseEntity.ok(Map.of("message", "Đã cập nhật địa chỉ mặc định thành công"));
	}

	// DELETE /api/addresses/user/{userId}/{addressId} (Xóa địa chỉ)
	@DeleteMapping("/user/{userId}/{addressId}")
	public ResponseEntity<Map<String, String>> deleteAddress(@PathVariable Integer userId,
			@PathVariable Integer addressId) {
		addressService.deleteAddress(userId, addressId);
		return ResponseEntity.ok(Map.of("message", "Đã xóa địa chỉ thành công"));
	}
}