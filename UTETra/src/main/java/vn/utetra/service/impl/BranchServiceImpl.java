package vn.utetra.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.request.BranchReq;
import vn.utetra.dto.response.NearbyBranchRes;
import vn.utetra.entity.Branch;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.UserRepository;
import vn.utetra.service.BranchService;
import vn.utetra.service.CloudinaryService;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class BranchServiceImpl implements BranchService {

	private static final String MANAGER_ROLE = "MANAGER";
	private static final BigDecimal DEFAULT_COMMISSION = new BigDecimal("5.00");
	private static final double EARTH_RADIUS_KM = 6371.0;

	private final BranchRepository branchRepository;
	private final UserRepository userRepository;
	private final CloudinaryService cloudinaryService;

	public BranchServiceImpl(BranchRepository branchRepository, UserRepository userRepository,
			CloudinaryService cloudinaryService) {
		this.branchRepository = branchRepository;
		this.userRepository = userRepository;
		this.cloudinaryService = cloudinaryService;
	}

	@Override
	public List<Branch> search(String keyword, BranchStatus status) {
		String searchKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
		return branchRepository.searchBranches(searchKeyword, status);
	}

	@Override
	public List<Branch> findApproved() {
		return branchRepository.findByStatus(BranchStatus.APPROVED);
	}

	@Override
	public Branch findById(Integer id) {
		return branchRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh với ID: " + id));
	}

	@Override
	@Transactional
	public Branch create(BranchReq req) {
		String name = cleanName(req.getName());
		checkDuplicateName(name, null);   // luật 2: không trùng tên
		User owner = findManager(req.getOwnerId()); // luật 3: người quản lý phải là Manager
		checkManagerFree(owner, null);              // luật 5: mỗi Manager chỉ quản lý 1 chi nhánh
		checkCoordinates(req);

		Branch branch = new Branch();
		branch.setOwner(owner);
		branch.setName(name);
		branch.setAddress(req.getAddress());
		branch.setLatitude(req.getLatitude());
		branch.setLongitude(req.getLongitude());
		branch.setPhone(req.getPhone());
		branch.setDescription(req.getDescription());
		branch.setCommissionRate(req.getCommissionRate() != null ? req.getCommissionRate() : DEFAULT_COMMISSION);
		// luật 1: Admin mở chi nhánh thì hoạt động luôn (form có chọn trạng thái thì theo form)
		branch.setStatus(req.getStatus() != null ? req.getStatus() : BranchStatus.APPROVED);

		if (req.getImageFile() != null && !req.getImageFile().isEmpty()) {
			String imageUrl = cloudinaryService.uploadImage(req.getImageFile(), "branches");
			branch.setImageCloudinaryId(imageUrl);
		}

		return branchRepository.save(branch);
	}

	@Override
	@Transactional
	public Branch update(Integer id, BranchReq req) {
		Branch branch = findById(id);

		String name = cleanName(req.getName());
		checkDuplicateName(name, id);
		checkCoordinates(req);

		if (req.getOwnerId() != null && !req.getOwnerId().equals(branch.getOwner().getId())) {
			User owner = findManager(req.getOwnerId());
			checkManagerFree(owner, id);
			branch.setOwner(owner);
		}

		branch.setName(name);
		branch.setAddress(req.getAddress());
		branch.setLatitude(req.getLatitude());
		branch.setLongitude(req.getLongitude());
		branch.setPhone(req.getPhone());
		branch.setDescription(req.getDescription());
		if (req.getCommissionRate() != null) {
			branch.setCommissionRate(req.getCommissionRate());
		}
		if (req.getStatus() != null && req.getStatus() != branch.getStatus()) {
			checkTransition(branch.getStatus(), req.getStatus()); // luật 4 áp dụng cả khi sửa qua form
			branch.setStatus(req.getStatus());
		}

		if (req.getImageFile() != null && !req.getImageFile().isEmpty()) {
			if (branch.getImageCloudinaryId() != null) {
				cloudinaryService.deleteImage(branch.getImageCloudinaryId());
			}
			String imageUrl = cloudinaryService.uploadImage(req.getImageFile(), "branches");
			branch.setImageCloudinaryId(imageUrl);
		}

		return branchRepository.save(branch);
	}

	@Override
	@Transactional
	public void changeStatus(Integer id, BranchStatus newStatus) {
		Branch branch = findById(id);
		if (newStatus == null || newStatus == branch.getStatus()) return; // không đổi gì
		checkTransition(branch.getStatus(), newStatus);
		branch.setStatus(newStatus);
		branchRepository.save(branch);
	}

	@Override
	public BranchReq toReq(Branch branch) {
		BranchReq req = new BranchReq();
		req.setId(branch.getId());
		req.setOwnerId(branch.getOwner() != null ? branch.getOwner().getId() : null);
		req.setName(branch.getName());
		req.setAddress(branch.getAddress());
		req.setLatitude(branch.getLatitude());
		req.setLongitude(branch.getLongitude());
		req.setPhone(branch.getPhone());
		req.setDescription(branch.getDescription());
		req.setCommissionRate(branch.getCommissionRate());
		req.setStatus(branch.getStatus());
		req.setImageCloudinaryId(branch.getImageCloudinaryId());
		return req;
	}

	@Override
	public List<User> findManagers() {
		return userRepository.findByRole_Name(MANAGER_ROLE);
	}

	// ================= GỢI Ý CHI NHÁNH GẦN NHẤT =================
	@Override
	public List<NearbyBranchRes> findNearby(Double lat, Double lng) {
		boolean coViTri = lat != null && lng != null;
		return findApproved().stream()
				.map(b -> new NearbyBranchRes(b.getId(), b.getName(), b.getAddress(), b.getPhone(), b.getLatitude(),
						b.getLongitude(), coViTri ? distanceKm(lat, lng, b.getLatitude(), b.getLongitude()) : null))
				// gần -> xa; chi nhánh không tính được khoảng cách xếp cuối, theo tên
				.sorted(Comparator.comparing(NearbyBranchRes::distanceKm, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(NearbyBranchRes::name))
				.toList();
	}

	@Override
	@Transactional(readOnly = true) // owner là LAZY: cần transaction để đọc
	public boolean isOwner(Integer branchId, String username) {
		if (branchId == null || username == null) return false;
		return branchRepository.findById(branchId)
				.map(b -> b.getOwner() != null && username.equals(b.getOwner().getUsername()))
				.orElse(false);
	}

	/** Khoảng cách đường chim bay (công thức Haversine), làm tròn 1 chữ số. null nếu chi nhánh chưa có tọa độ */
	private Double distanceKm(double lat, double lng, BigDecimal branchLat, BigDecimal branchLng) {
		if (branchLat == null || branchLng == null) return null;
		double dLat = Math.toRadians(branchLat.doubleValue() - lat);
		double dLng = Math.toRadians(branchLng.doubleValue() - lng);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat))
				* Math.cos(Math.toRadians(branchLat.doubleValue())) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		double km = EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return Math.round(km * 10) / 10.0;
	}

	// ================= LUẬT NGHIỆP VỤ =================

	private String cleanName(String name) {
		if (name == null || name.isBlank()) throw new BusinessException("Tên chi nhánh không được để trống");
		return name.trim();
	}

	/** Luật 2: tên chi nhánh không trùng (không phân biệt hoa thường). excludeId = chi nhánh đang sửa */
	private void checkDuplicateName(String name, Integer excludeId) {
		boolean duplicated = branchRepository.findAll().stream()
				.anyMatch(b -> b.getName().trim().equalsIgnoreCase(name) && !b.getId().equals(excludeId));
		if (duplicated) throw new BusinessException("Tên chi nhánh \"" + name + "\" đã tồn tại");
	}

	/** Luật 3: người quản lý chi nhánh phải là tài khoản có role MANAGER */
	/**
	 * Luật 5 (chuỗi tự mở chi nhánh): Admin mở chi nhánh rồi chỉ định Manager,
	 * mỗi Manager chỉ quản lý 1 chi nhánh. exceptBranchId = chi nhánh đang sửa (bỏ qua chính nó).
	 */
	private void checkManagerFree(User manager, Integer exceptBranchId) {
		branchRepository.findByOwnerIdOrderByIdAsc(manager.getId()).stream()
				.filter(b -> !b.getId().equals(exceptBranchId))
				.findFirst()
				.ifPresent(b -> {
					throw new BusinessException("Manager " + manager.getUsername()
							+ " đang quản lý chi nhánh \"" + b.getName() + "\". Mỗi Manager chỉ quản lý 1 chi nhánh");
				});
	}

	private User findManager(Integer ownerId) {
		if (ownerId == null) throw new BusinessException("Vui lòng chọn Manager quản lý chi nhánh");
		User owner = userRepository.findById(ownerId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng #" + ownerId));
		if (owner.getRole() == null || !MANAGER_ROLE.equalsIgnoreCase(owner.getRole().getName())) {
			throw new BusinessException("Người quản lý chi nhánh phải là tài khoản Manager");
		}
		return owner;
	}

	/** Tọa độ: nhập đủ cả 2 hoặc bỏ trống cả 2 */
	private void checkCoordinates(BranchReq req) {
		if ((req.getLatitude() == null) != (req.getLongitude() == null)) {
			throw new BusinessException("Vui lòng nhập đủ cả vĩ độ và kinh độ (hoặc bỏ trống cả hai)");
		}
	}

	/**
	 * Luật 4: Admin bật/tắt chi nhánh
	 *   Đang hoạt động -> Tạm ngưng
	 *   Tạm ngưng      -> Đang hoạt động (mở lại)
	 */
	private void checkTransition(BranchStatus from, BranchStatus to) {
		boolean ok = switch (from) {
			case APPROVED -> to == BranchStatus.SUSPENDED;
			case SUSPENDED -> to == BranchStatus.APPROVED;
		};
		if (!ok) throw new BusinessException(
				"Không thể chuyển chi nhánh từ " + from.getLabel() + " sang " + to.getLabel());
	}
}