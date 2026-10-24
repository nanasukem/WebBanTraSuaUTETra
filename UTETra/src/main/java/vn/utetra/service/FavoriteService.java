package vn.utetra.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.dto.response.ProductCardRes;
import vn.utetra.entity.Favorite;
import vn.utetra.entity.Product;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.FavoriteRepository;
import vn.utetra.repository.ProductRepository;
import vn.utetra.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class FavoriteService {

	private final FavoriteRepository favoriteRepository;
	private final ProductRepository productRepository;
	private final UserRepository userRepository;
	private final ProductHighlightService productHighlightService;

	public FavoriteService(FavoriteRepository favoriteRepository, ProductRepository productRepository,
			UserRepository userRepository, ProductHighlightService productHighlightService) {
		this.favoriteRepository = favoriteRepository;
		this.productRepository = productRepository;
		this.userRepository = userRepository;
		this.productHighlightService = productHighlightService;
	}

	@Transactional
	public boolean toggle(Integer userId, Integer productId) {
		Optional<Favorite> existing = favoriteRepository.findByUserIdAndProductId(userId, productId);
		if (existing.isPresent()) {
			favoriteRepository.delete(existing.get());
			favoriteRepository.flush();
			return false;
		}
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món #" + productId));
		Favorite f = new Favorite();
		f.setUser(userRepository.getReferenceById(userId));
		f.setProduct(product);
		favoriteRepository.saveAndFlush(f);
		return true;
	}

	public boolean isFavorite(Integer userId, Integer productId) {
		return userId != null && favoriteRepository.existsByUserIdAndProductId(userId, productId);
	}

	public long countOfProduct(Integer productId) {
		return favoriteRepository.countByProductId(productId);
	}

	public List<ProductCardRes> list(Integer userId) {
		List<Product> products = favoriteRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
				.map(Favorite::getProduct).toList();
		return productHighlightService.toCards(products);
	}

	public long count(Integer userId) {
		return favoriteRepository.countByUserId(userId);
	}
}
