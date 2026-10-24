package vn.utetra.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.utetra.entity.Product;
import vn.utetra.service.AdminProductService;
import vn.utetra.service.GuestHomeService;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {

	private final AdminProductService adminProductService;
	private final GuestHomeService guestHomeService;

	public ProductApiController(AdminProductService adminProductService, GuestHomeService guestHomeService) {
		this.adminProductService = adminProductService;
		this.guestHomeService = guestHomeService;
	}

	@GetMapping
	public ResponseEntity<List<Product>> getAllProducts() {
		return ResponseEntity.ok(adminProductService.getAllProducts());
	}

	@GetMapping("/best-sellers")
	public ResponseEntity<List<Product>> getBestSellers() {
		return ResponseEntity.ok(guestHomeService.getBestSellerProducts());
	}

	@GetMapping("/{id}")
	public ResponseEntity<Product> getProductById(@PathVariable Integer id) {
		return ResponseEntity.ok(adminProductService.getProductById(id));
	}
}