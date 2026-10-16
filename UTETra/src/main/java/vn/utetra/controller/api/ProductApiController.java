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

	// GET /api/products
	@GetMapping
	public ResponseEntity<List<Product>> getAllProducts() {
		return ResponseEntity.ok(adminProductService.getAllProducts());
	}

	// GET /api/products/best-sellers (Sản phẩm bán > 10 ly cho trang chủ Guest)
	@GetMapping("/best-sellers")
	public ResponseEntity<List<Product>> getBestSellers() {
		return ResponseEntity.ok(guestHomeService.getBestSellerProducts());
	}

	// GET /api/products/{id}
	@GetMapping("/{id}")
	public ResponseEntity<Product> getProductById(@PathVariable Integer id) {
		return ResponseEntity.ok(adminProductService.getProductById(id));
	}
}