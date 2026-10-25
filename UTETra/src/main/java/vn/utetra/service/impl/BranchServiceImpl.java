package vn.utetra.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
	private static final double EARTH_RADIUS_KM = 6371.0;

	private final BranchRepository branchRepository;
	private final UserRepository userRepository;
	private final CloudinaryService cloudinaryService;
	@org.springframework.beans.factory.annotation.Autowired
	private vn.utetra.repository.OrderRepository orderRepository;
	@org.springframework.beans.factory.annotation.Autowired
	private vn.utetra.repository.VoucherRepository voucherRepository;

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
	public List<Branch> findAll() {
		return branchRepository.findAll();
	}

	@Override
	public java.util.Optional<Branch> findOptional(Integer id) {
		return id == null ? java.util.Optional.empty() : branchRepository.findById(id);
	}

	@Override
	public List<Branch> findByOwner(Integer ownerId) {
		return branchRepository.findByOwnerIdOrderByIdAsc(ownerId);
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
		checkDuplicateName(name, null);
		User owner = findManager(req.getOwnerId());
		checkManagerFree(owner, null);
		checkCoordinates(req);

		Branch branch = new Branch();
		branch.setOwner(owner);
		branch.setName(name);
		branch.setAddress(req.getAddress());
		branch.setLatitude(req.getLatitude());
		branch.setLongitude(req.getLongitude());
		branch.setPhone(req.getPhone());
		branch.setDescription(req.getDescription());
		setHours(branch, req);
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
		setHours(branch, req);
		if (req.getStatus() != null && req.getStatus() != branch.getStatus()) {
			checkTransition(branch.getStatus(), req.getStatus());
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
		if (newStatus == null || newStatus == branch.getStatus())
			return;
		checkTransition(branch.getStatus(), newStatus);
		branch.setStatus(newStatus);
		branchRepository.save(branch);
	}

	@Override
	@Transactional
	public String delete(Integer id) {
		Branch branch = findById(id);
		long orders = orderRepository.countByBranch_Id(id);
		if (orders > 0) {
			throw new BusinessException("Chi nhánh \"" + branch.getName() + "\" đã có " + orders
					+ " đơn hàng nên không xoá được (để giữ lịch sử đơn và doanh thu). Hãy chuyển sang Tạm ngưng để ngừng nhận đơn.");
		}
		List<User> staff = userRepository.findByBranch_Id(id);
		for (User u : staff) {
			u.setBranch(null);
		}
		userRepository.saveAll(staff);
		voucherRepository.deleteAll(voucherRepository.findByBranch_IdOrderByCreatedAtDesc(id));
		String image = branch.getImageCloudinaryId();
		String name = branch.getName();
		branchRepository.delete(branch);
		branchRepository.flush();
		if (image != null && !image.isBlank()) {
			try {
				cloudinaryService.deleteImage(image);
			} catch (Exception ignored) {
			}
		}
		return staff.isEmpty() ? "Đã xoá chi nhánh \"" + name + "\""
				: "Đã xoá chi nhánh \"" + name + "\". " + staff.size()
						+ " shipper của chi nhánh chuyển về \"Chưa phân công\", hãy phân công lại.";
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
		req.setOpenTime(branch.getOpenTime());
		req.setCloseTime(branch.getCloseTime());
		req.setStatus(branch.getStatus());
		req.setImageCloudinaryId(branch.getImageCloudinaryId());
		return req;
	}

	@Override
	public List<User> findManagers() {
		return userRepository.findByRole_Name(MANAGER_ROLE);
	}

	private static void setHours(Branch branch, BranchReq req) {
		branch.setOpenTime(req.getOpenTime() != null ? req.getOpenTime() : java.time.LocalTime.of(7, 0));
		branch.setCloseTime(req.getCloseTime() != null ? req.getCloseTime() : java.time.LocalTime.of(22, 0));
	}

	@Override
	public List<NearbyBranchRes> findNearby(Double lat, Double lng) {
		boolean coViTri = lat != null && lng != null;
		java.time.LocalTime now = java.time.LocalTime.now();
		return findApproved().stream()
				.map(b -> new NearbyBranchRes(b.getId(), b.getName(), b.getAddress(), b.getPhone(), b.getLatitude(),
						b.getLongitude(), coViTri ? distanceKm(lat, lng, b.getLatitude(), b.getLongitude()) : null,
						b.isOpenAt(now), b.getHoursLabel(), b.openStatusAt(now)))
				.sorted(Comparator
						.comparing(NearbyBranchRes::distanceKm, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(NearbyBranchRes::name))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isOwner(Integer branchId, String username) {
		if (branchId == null || username == null)
			return false;
		return branchRepository.findById(branchId)
				.map(b -> b.getOwner() != null && username.equals(b.getOwner().getUsername())).orElse(false);
	}

	private Double distanceKm(double lat, double lng, BigDecimal branchLat, BigDecimal branchLng) {
		if (branchLat == null || branchLng == null)
			return null;
		double dLat = Math.toRadians(branchLat.doubleValue() - lat);
		double dLng = Math.toRadians(branchLng.doubleValue() - lng);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat))
				* Math.cos(Math.toRadians(branchLat.doubleValue())) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		double km = EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return Math.round(km * 10) / 10.0;
	}

	private String cleanName(String name) {
		if (name == null || name.isBlank())
			throw new BusinessException("Tên chi nhánh không được để trống");
		return name.trim();
	}

	private void checkDuplicateName(String name, Integer excludeId) {
		boolean duplicated = branchRepository.findAll().stream()
				.anyMatch(b -> b.getName().trim().equalsIgnoreCase(name) && !b.getId().equals(excludeId));
		if (duplicated)
			throw new BusinessException("Tên chi nhánh \"" + name + "\" đã tồn tại");
	}

	private void checkManagerFree(User manager, Integer exceptBranchId) {
		branchRepository.findByOwnerIdOrderByIdAsc(manager.getId()).stream()
				.filter(b -> !b.getId().equals(exceptBranchId)).findFirst().ifPresent(b -> {
					throw new BusinessException("Manager " + manager.getUsername() + " đang quản lý chi nhánh \""
							+ b.getName() + "\". Mỗi Manager chỉ quản lý 1 chi nhánh");
				});
	}

	private User findManager(Integer ownerId) {
		if (ownerId == null)
			throw new BusinessException("Vui lòng chọn Manager quản lý chi nhánh");
		User owner = userRepository.findById(ownerId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng #" + ownerId));
		if (owner.getRole() == null || !MANAGER_ROLE.equalsIgnoreCase(owner.getRole().getName())) {
			throw new BusinessException("Người quản lý chi nhánh phải là tài khoản Manager");
		}
		return owner;
	}

	private void checkCoordinates(BranchReq req) {
		if ((req.getLatitude() == null) != (req.getLongitude() == null)) {
			throw new BusinessException("Vui lòng nhập đủ cả vĩ độ và kinh độ (hoặc bỏ trống cả hai)");
		}
	}

	private void checkTransition(BranchStatus from, BranchStatus to) {
		boolean ok = switch (from) {
		case APPROVED -> to == BranchStatus.SUSPENDED;
		case SUSPENDED -> to == BranchStatus.APPROVED;
		};
		if (!ok)
			throw new BusinessException("Không thể chuyển chi nhánh từ " + from.getLabel() + " sang " + to.getLabel());
	}

	@Override
	@Transactional
	public Branch updateManagerHomepage(Integer branchId, String username, String description,
			MultipartFile imageFile) {

		if (branchId == null) {
			throw new BusinessException("Chi nhánh không hợp lệ");
		}

		if (username == null || username.isBlank()) {
			throw new BusinessException("Không xác định được tài khoản Manager");
		}

		Branch branch = branchRepository.findById(branchId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh với ID: " + branchId));

		if (branch.getOwner() == null || !username.equals(branch.getOwner().getUsername())) {
			throw new BusinessException("Bạn không có quyền chỉnh sửa trang chủ chi nhánh này");
		}

		String cleanDescription = description == null ? "" : description.trim();

		if (cleanDescription.length() > 3000) {
			throw new BusinessException("Mô tả trang chủ không được vượt quá 3000 ký tự");
		}

		branch.setDescription(cleanDescription);

		if (imageFile != null && !imageFile.isEmpty()) {
			if (imageFile.getSize() > 5 * 1024 * 1024) {
				throw new BusinessException("Ảnh bìa không được vượt quá 5 MB");
			}

			String contentType = imageFile.getContentType();

			if (contentType == null || !(contentType.equalsIgnoreCase("image/jpeg")
					|| contentType.equalsIgnoreCase("image/png") || contentType.equalsIgnoreCase("image/webp"))) {
				throw new BusinessException("Ảnh bìa chỉ chấp nhận định dạng JPG, PNG hoặc WEBP");
			}

			String newImageUrl = cloudinaryService.uploadImage(imageFile, "branches");

			if (newImageUrl == null || newImageUrl.isBlank()) {
				throw new BusinessException("Không thể tải ảnh bìa lên Cloudinary");
			}

			branch.setImageCloudinaryId(newImageUrl);
		}

		return branchRepository.save(branch);
	}
}