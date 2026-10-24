package vn.utetra.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpSession;
import vn.utetra.entity.Branch;
import vn.utetra.entity.Product;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.repository.BranchProductRepository;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.ProductRepository;

@Service
@Transactional(readOnly = true)
public class MenuBranchService {

	public static final String SESSION_KEY = "menuBranchId";
	public static final String SESSION_AUTO = "menuBranchAuto";
	public static final String SESSION_NONE = "menuBranchNone";

	private final BranchRepository branchRepository;
	private final BranchProductRepository branchProductRepository;
	private final ProductRepository productRepository;
	private final ToppingService toppingService;
	private final vn.utetra.repository.AddressRepository addressRepository;

	public MenuBranchService(BranchRepository branchRepository, BranchProductRepository branchProductRepository,
			ProductRepository productRepository, ToppingService toppingService,
			vn.utetra.repository.AddressRepository addressRepository) {
		this.addressRepository = addressRepository;
		this.branchRepository = branchRepository;
		this.branchProductRepository = branchProductRepository;
		this.productRepository = productRepository;
		this.toppingService = toppingService;
	}

	public List<Branch> openBranches() {
		return branchRepository.findByStatus(BranchStatus.APPROVED).stream()
				.sorted(Comparator.comparing(Branch::getId)).toList();
	}

	public boolean locked(HttpSession session) {
		return "MANAGER".equals(role(session));
	}

	public Branch current(HttpSession session) {
		if (session == null) return null;
		if (locked(session)) {
			User u = (User) session.getAttribute("user");
			return branchRepository.findAll().stream()
					.filter(b -> b.getOwner() != null && b.getOwner().getId().equals(u.getId())).findFirst().orElse(null);
		}
		Object id = session.getAttribute(SESSION_KEY);
		if (id instanceof Integer bid) {
			Branch b = branchRepository.findById(bid).filter(x -> x.getStatus() == BranchStatus.APPROVED).orElse(null);
			if (b != null) return b;
		}
		if (!Boolean.TRUE.equals(session.getAttribute(SESSION_NONE)) && "USER".equals(role(session))) {
			User u = (User) session.getAttribute("user");
			Branch near = addressRepository.findByUserIdAndIsDefaultTrue(u.getId())
					.filter(a -> a.getLatitude() != null && a.getLongitude() != null)
					.map(a -> nearest(a.getLatitude().doubleValue(), a.getLongitude().doubleValue())).orElse(null);
			if (near != null) {
				session.setAttribute(SESSION_KEY, near.getId());
				session.setAttribute(SESSION_AUTO, true);
				return near;
			}
		}
		return null;
	}

	public boolean auto(HttpSession session) {
		return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_AUTO));
	}

	public Branch nearest(double lat, double lng) {
		return openBranches().stream().filter(b -> b.getLatitude() != null && b.getLongitude() != null)
				.min(Comparator.comparingDouble(b -> km(lat, lng, b.getLatitude().doubleValue(), b.getLongitude().doubleValue())))
				.orElse(null);
	}

	public Branch chooseNearest(HttpSession session, double lat, double lng) {
		if (locked(session) || session.getAttribute(SESSION_KEY) != null
				|| Boolean.TRUE.equals(session.getAttribute(SESSION_NONE))) {
			return current(session);
		}
		Branch b = nearest(lat, lng);
		if (b != null) {
			session.setAttribute(SESSION_KEY, b.getId());
			session.setAttribute(SESSION_AUTO, true);
		}
		return b;
	}

	private static double km(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
		double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
				* Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * 6371 * Math.asin(Math.sqrt(h));
	}

	public Integer currentId(HttpSession session) {
		Branch b = current(session);
		return b == null ? null : b.getId();
	}

	public void choose(HttpSession session, Integer branchId) {
		if (locked(session)) return;
		boolean ok = branchId != null && branchRepository.findById(branchId)
				.map(b -> b.getStatus() == BranchStatus.APPROVED).orElse(false);
		session.removeAttribute(SESSION_AUTO);
		if (ok) {
			session.setAttribute(SESSION_KEY, branchId);
			session.removeAttribute(SESSION_NONE);
		} else {
			session.removeAttribute(SESSION_KEY);
			session.setAttribute(SESSION_NONE, true);
		}
	}

	public Set<Integer> outProductIds(Integer branchId) {
		if (branchId == null) return Set.of();
		Set<Integer> ids = new HashSet<>(branchProductRepository.findUnavailableProductIds(branchId));
		Set<Integer> hetTopping = toppingService.unavailableToppingIds(branchId);
		if (!hetTopping.isEmpty()) {
			for (Product p : productRepository.findByToppingIsNotNull()) {
				if (hetTopping.contains(p.getTopping().getId())) ids.add(p.getId());
			}
		}
		return ids;
	}

	public Set<Integer> outToppingIds(Integer branchId) {
		return toppingService.unavailableToppingIds(branchId);
	}

	private static String role(HttpSession session) {
		Object u = session == null ? null : session.getAttribute("user");
		if (!(u instanceof User user) || user.getRole() == null || user.getRole().getName() == null) return "";
		return user.getRole().getName().trim().toUpperCase();
	}
}
