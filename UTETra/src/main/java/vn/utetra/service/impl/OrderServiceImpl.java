package vn.utetra.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.dto.request.CheckoutReq;
import vn.utetra.dto.response.CartItemRes;
import vn.utetra.dto.response.CartRes;
import vn.utetra.dto.response.CheckoutPreviewRes;
import vn.utetra.dto.response.OrderSummaryRes;
import vn.utetra.dto.response.VoucherOptionRes;
import vn.utetra.entity.*;
import vn.utetra.entity.enums.*;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.*;
import vn.utetra.service.CartService;
import vn.utetra.service.MemberService;
import vn.utetra.service.OrderNotifier;
import vn.utetra.service.OrderService;
import vn.utetra.service.VoucherService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    /** SĐT Việt Nam: 10 số, bắt đầu bằng 0 */
    private static final String PHONE_REGEX = "0\\d{9}";

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final CartService cartService;
    private final VoucherService voucherService;
    private final MemberService memberService;
    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final BranchProductRepository branchProductRepository;
    private final AddressRepository addressRepository;
    private final CarrierRepository carrierRepository;
    private final VoucherRepository voucherRepository;
    private final ProductRepository productRepository;
    private final ToppingRepository toppingRepository;
    private final UserRepository userRepository;
    private final OrderNotifier orderNotifier;

    public OrderServiceImpl(
            CartService cartService,
            VoucherService voucherService,
            MemberService memberService,
            OrderRepository orderRepository,
            BranchRepository branchRepository,
            BranchProductRepository branchProductRepository,
            AddressRepository addressRepository,
            CarrierRepository carrierRepository,
            VoucherRepository voucherRepository,
            ProductRepository productRepository,
            ToppingRepository toppingRepository,
            UserRepository userRepository,
            OrderNotifier orderNotifier) {

        this.cartService = cartService;
        this.voucherService = voucherService;
        this.memberService = memberService;
        this.orderRepository = orderRepository;
        this.branchRepository = branchRepository;
        this.branchProductRepository = branchProductRepository;
        this.addressRepository = addressRepository;
        this.carrierRepository = carrierRepository;
        this.voucherRepository = voucherRepository;
        this.productRepository = productRepository;
        this.toppingRepository = toppingRepository;
        this.userRepository = userRepository;
        this.orderNotifier = orderNotifier;
    }

    /** Kết quả tính toán dùng chung cho preview và placeOrder */
    private static class Calc {
        CartRes cart;
        Branch branch;
        DeliveryType deliveryType;
        Address address;
        Carrier carrier;

        String receiverName;
        String receiverPhone;

        Voucher productVoucher;
        Voucher shippingVoucher;

        BigDecimal itemsTotal = BigDecimal.ZERO;
        BigDecimal productDiscount = BigDecimal.ZERO;
        BigDecimal memberDiscount = BigDecimal.ZERO;
        String memberTierName;

        BigDecimal shippingFee = BigDecimal.ZERO;
        BigDecimal shippingDiscount = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        List<String> unavailable = new ArrayList<>();

        String productVoucherError;
        String shippingVoucherError;

        List<String> errors = new ArrayList<>();
    }

    // ================= XEM TRƯỚC ĐƠN HÀNG =================

    @Override
    public CheckoutPreviewRes preview(Integer userId, CheckoutReq req) {

        Calc c = calculate(userId, req, false);

        boolean canOrder = c.errors.isEmpty()
                && c.productVoucherError == null
                && c.shippingVoucherError == null;

        return new CheckoutPreviewRes(
                c.cart.items(),
                c.cart.totalQty(),
                c.itemsTotal,
                c.productDiscount,
                c.memberDiscount,
                c.memberTierName,
                c.shippingFee,
                c.shippingDiscount,
                c.totalAmount,
                c.unavailable,
                c.productVoucherError,
                c.shippingVoucherError,
                c.errors,
                canOrder
        );
    }

    // ================= DANH SÁCH VOUCHER =================

    @Override
    public List<VoucherOptionRes> voucherOptions(Integer userId, CheckoutReq req) {

        Calc c = calculate(userId, req, false);

        Integer branchId = c.branch == null ? null : c.branch.getId();

        boolean newCustomer = memberService.isNewCustomer(userId);

        List<VoucherOptionRes> result = new ArrayList<>();

        for (Voucher v : voucherService.findUsable(branchId)) {

            if (v.getAudience() == VoucherAudience.NEW_USER && !newCustomer) {
                continue;
            }

            String reason = null;
            BigDecimal discount = BigDecimal.ZERO;

            if (v.getApplyTo() == VoucherApplyTo.SHIPPING
                    && c.deliveryType == DeliveryType.PICKUP) {

                reason = "Chỉ dùng cho đơn giao tận nơi";

            } else if (c.itemsTotal.compareTo(v.getMinOrderValue()) < 0) {

                reason = "Mua thêm "
                        + money(v.getMinOrderValue().subtract(c.itemsTotal))
                        + " để dùng mã này";

            } else {
                try {
                    discount = voucherService.calculateDiscount(
                            v.getCode(),
                            branchId,
                            userId,
                            req.getPaymentMethod(),
                            c.itemsTotal,
                            c.shippingFee
                    );
                } catch (BusinessException e) {
                    reason = e.getMessage();
                }
            }

            result.add(new VoucherOptionRes(
                    v.getCode(),
                    v.getName(),
                    v.getApplyTo().name(),
                    v.getDiscountType().name(),
                    v.getDiscountValue(),
                    v.getMaxDiscount(),
                    v.getMinOrderValue(),
                    v.getEndDate().format(DATE),
                    tags(v),
                    reason == null,
                    reason,
                    discount
            ));
        }

        result.sort(
                Comparator.comparing(VoucherOptionRes::usable)
                        .reversed()
                        .thenComparing(
                                VoucherOptionRes::discount,
                                Comparator.reverseOrder()
                        )
        );

        return result;
    }

    private List<String> tags(Voucher v) {

        List<String> tags = new ArrayList<>();

        if (v.getBranch() != null) {
            tags.add("Riêng " + v.getBranch().getName());
        }

        if (v.getAudience() == VoucherAudience.NEW_USER) {
            tags.add("Bạn mới");
        }

        if (v.getAudience() == VoucherAudience.MIN_TIER
                && v.getMinTier() != null) {
            tags.add("Hạng " + v.getMinTier().getName() + "+");
        }

        if (v.getPaymentMethod() != null) {
            tags.add("Chỉ thanh toán " + paymentLabel(v.getPaymentMethod()));
        }

        if (v.getPerUserLimit() != null) {
            tags.add("Mỗi người " + v.getPerUserLimit() + " lần");
        }

        return tags;
    }

    // ================= TÓM TẮT ĐƠN HÀNG =================

    @Override
    public OrderSummaryRes getOrderSummary(Integer userId, Integer orderId) {

        Order o = orderRepository.findById(orderId)
                .filter(x -> x.getUser().getId().equals(userId))
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Không tìm thấy đơn hàng #" + orderId
                        )
                );

        int qty = o.getDetails()
                .stream()
                .mapToInt(d -> d.getQuantity())
                .sum();

        return new OrderSummaryRes(
                o.getId(),
                o.getBranch().getName(),
                o.getBranch().getAddress(),
                o.getBranch().getPhone(),
                o.getDeliveryType(),
                o.getReceiverName(),
                o.getReceiverPhone(),
                o.getDeliveryAddress(),
                o.getCarrier() == null ? null : o.getCarrier().getName(),
                o.getTotalAmount(),
                o.getPaymentMethod(),
                o.getStatus(),
                qty,
                o.getCreatedAt().format(DATE_TIME)
        );
    }

    // ================= ĐẶT HÀNG =================

    @Override
    @Transactional
    public Order placeOrder(Integer userId, CheckoutReq req) {

        Calc c = calculate(userId, req, true);

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Không tìm thấy người dùng #" + userId
                        )
                );

        // 1. Tạo đơn hàng
        Order o = new Order();

        o.setUser(user);
        o.setBranch(c.branch);
        o.setDeliveryType(c.deliveryType);
        o.setCarrier(c.carrier);
        o.setProductVoucher(c.productVoucher);
        o.setShippingVoucher(c.shippingVoucher);

        o.setReceiverName(c.receiverName);
        o.setReceiverPhone(c.receiverPhone);

        o.setDeliveryAddress(
                c.address == null ? null : c.address.getAddressLine()
        );

        o.setDeliveryNote(
                c.address == null ? null : c.address.getNote()
        );

        o.setNote(
                req.getNote() == null || req.getNote().isBlank()
                        ? null
                        : req.getNote().trim()
        );

        o.setItemsTotal(c.itemsTotal);
        o.setProductDiscount(c.productDiscount);
        o.setMemberDiscount(c.memberDiscount);
        o.setMemberTierName(c.memberTierName);
        o.setShippingFee(c.shippingFee);
        o.setShippingDiscount(c.shippingDiscount);
        o.setTotalAmount(c.totalAmount);
        o.setCommissionAmount(commission(c));

        o.setStatus(OrderStatus.PENDING);
        o.setPaymentMethod(req.getPaymentMethod());
        o.setPaymentStatus(PaymentStatus.UNPAID);

        // 2. Lưu chi tiết món và topping
        for (CartItemRes line : c.cart.items()) {

            OrderDetail d = new OrderDetail();

            d.setProduct(
                    productRepository.getReferenceById(line.productId())
            );

            d.setSize(line.size());
            d.setSugarLevel(line.sugarLevel());
            d.setIceLevel(line.iceLevel());
            d.setQuantity(line.quantity());
            d.setUnitPrice(line.unitPrice());
            d.setSubtotal(line.lineTotal());
            d.setNote(line.note());

            for (CartItemRes.ToppingRes t : line.toppings()) {

                OrderDetailTopping odt = new OrderDetailTopping();

                odt.setTopping(
                        toppingRepository.getReferenceById(t.id())
                );

                odt.setPrice(t.price());

                d.addTopping(odt);
            }

            o.addDetail(d);
        }

        // 3. Lưu đơn hàng
        Order saved = orderRepository.save(o);

        /*
         * 4. Xử lý theo phương thức thanh toán.
         *
         * COD:
         * - Xóa giỏ hàng sau khi tạo đơn.
         * - Thông báo cửa hàng.
         *
         * VNPAY/MOMO:
         * - Đơn vẫn UNPAID.
         * - Giữ nguyên giỏ hàng.
         * - Chưa thông báo cửa hàng.
         * - Chờ controller xác nhận thanh toán thành công.
         */
        PaymentMethod method = saved.getPaymentMethod();

        if (method == PaymentMethod.COD) {

            // Ghi nhận voucher cho đơn COD.
            markVouchersUsed(c, userId, saved.getId());

            // Xóa giỏ hàng và thông báo cửa hàng.
            cartService.clear(userId);
            orderNotifier.onNewOrder(saved);

        } else if (method == PaymentMethod.VNPAY
                || method == PaymentMethod.MOMO) {

            /*
             * Không xóa giỏ hàng ở đây.
             * Không gọi orderNotifier ở đây.
             * Không đánh dấu voucher đã sử dụng ở đây.
             *
             * Khi thanh toán thành công, controller phải:
             * 1. Xác minh kết quả thanh toán.
             * 2. Cập nhật PaymentStatus thành PAID.
             * 3. Ghi nhận voucher đã sử dụng.
             * 4. Xóa giỏ hàng của đúng user sở hữu đơn.
             * 5. Thông báo cửa hàng.
             *
             * Không được thực hiện các bước trên chỉ dựa vào
             * một tham số success do trình duyệt gửi lên.
             */

        } else {
            throw new BusinessException("Phương thức thanh toán không hợp lệ");
        }

        return saved;
    }

    /**
     * Ghi nhận voucher đã sử dụng.
     *
     * Phương thức này được gọi ngay cho COD.
     * Với VNPAY/MOMO, chỉ gọi sau khi xác nhận thanh toán thành công.
     */
    private void markVouchersUsed(Calc c, Integer userId, Integer orderId) {

        if (c.productVoucher != null) {
            voucherService.markUsed(
                    c.productVoucher.getId(),
                    userId,
                    orderId
            );
        }

        if (c.shippingVoucher != null) {
            voucherService.markUsed(
                    c.shippingVoucher.getId(),
                    userId,
                    orderId
            );
        }
    }

    // ================= TÍNH TOÁN DÙNG CHUNG =================

    /**
     * strict = true: gặp lỗi thì ném BusinessException.
     * strict = false: ghi lỗi để trang checkout hiển thị.
     */
    private Calc calculate(Integer userId, CheckoutReq req, boolean strict) {

        Calc c = new Calc();

        // Giỏ hàng
        c.cart = cartService.getCart(userId);

        if (c.cart.items().isEmpty()) {
            fail(c, strict, "Giỏ hàng đang trống");
        }

        c.itemsTotal = c.cart.subtotal();

        // 1. Chi nhánh
        if (req.getBranchId() == null) {

            fail(c, strict, "Vui lòng chọn chi nhánh");

        } else {

            c.branch = branchRepository.findById(req.getBranchId())
                    .orElse(null);

            if (c.branch == null) {

                fail(c, strict, "Chi nhánh không tồn tại");

            } else if (c.branch.getStatus() != BranchStatus.APPROVED) {

                fail(
                        c,
                        strict,
                        "Chi nhánh " + c.branch.getName()
                                + " hiện không nhận đơn"
                );
            }
        }

        // 2. Kiểm tra món và topping còn bán
        Set<Integer> hetOChiNhanh = c.branch == null
                ? Set.of()
                : new HashSet<>(
                        branchProductRepository.findUnavailableProductIds(
                                c.branch.getId()
                        )
                );

        for (CartItemRes line : c.cart.items()) {

            boolean conBan = productRepository.findById(line.productId())
                    .map(p -> Boolean.TRUE.equals(p.getIsAvailable()))
                    .orElse(false);

            conBan = conBan
                    && !hetOChiNhanh.contains(line.productId())
                    && line.toppings()
                            .stream()
                            .allMatch(t ->
                                    toppingRepository.findById(t.id())
                                            .map(tp -> Boolean.TRUE.equals(
                                                    tp.getIsAvailable()
                                            ))
                                            .orElse(false)
                            );

            if (!conBan && !c.unavailable.contains(line.productName())) {
                c.unavailable.add(line.productName());
            }
        }

        if (!c.unavailable.isEmpty()) {

            String taiDau = c.branch == null
                    ? ""
                    : " tại " + c.branch.getName();

            fail(
                    c,
                    strict,
                    "Các món sau đã hết" + taiDau + ": "
                            + String.join(", ", c.unavailable)
                            + ". Vui lòng xoá khỏi giỏ hoặc chọn chi nhánh khác"
            );
        }

        // 3. Hình thức nhận hàng
        c.deliveryType = req.getDeliveryType() == null
                ? DeliveryType.DELIVERY
                : req.getDeliveryType();

        if (c.deliveryType == DeliveryType.DELIVERY) {

            // 3a. Địa chỉ giao hàng
            if (req.getAddressId() == null) {

                fail(c, strict, "Vui lòng chọn địa chỉ nhận hàng");

            } else {

                c.address = addressRepository.findById(req.getAddressId())
                        .filter(a -> a.getUser().getId().equals(userId))
                        .orElse(null);

                if (c.address == null) {

                    fail(c, strict, "Địa chỉ nhận hàng không hợp lệ");

                } else {

                    c.receiverName = c.address.getReceiverName();
                    c.receiverPhone = c.address.getReceiverPhone();
                }
            }

            // 3b. Nhà vận chuyển
            if (req.getCarrierId() == null) {

                fail(c, strict, "Vui lòng chọn nhà vận chuyển");

            } else {

                c.carrier = carrierRepository.findById(req.getCarrierId())
                        .filter(cr -> Boolean.TRUE.equals(cr.getIsActive()))
                        .orElse(null);

                if (c.carrier == null) {

                    fail(c, strict, "Nhà vận chuyển không khả dụng");

                } else {

                    c.shippingFee = c.carrier.getFee();
                }
            }

        } else {

            // 3c. Đến lấy tại cửa hàng
            c.receiverName = trimToNull(req.getReceiverName());
            c.receiverPhone = trimToNull(req.getReceiverPhone());

            if (c.receiverName == null) {
                fail(c, strict, "Vui lòng nhập tên người đến lấy");
            }

            if (c.receiverPhone == null
                    || !c.receiverPhone.matches(PHONE_REGEX)) {

                fail(
                        c,
                        strict,
                        "Số điện thoại không hợp lệ "
                                + "(10 số, bắt đầu bằng 0)"
                );
            }

            c.shippingFee = BigDecimal.ZERO;
        }

        // 4. Phương thức thanh toán
        if (req.getPaymentMethod() == null) {

            fail(c, strict, "Vui lòng chọn phương thức thanh toán");

        } else if (
                req.getPaymentMethod() != PaymentMethod.COD
                        && req.getPaymentMethod() != PaymentMethod.VNPAY
                        && req.getPaymentMethod() != PaymentMethod.MOMO
        ) {

            fail(c, strict, "Phương thức thanh toán không hợp lệ");
        }

        // 5. Voucher giảm tiền món
        Integer branchId = c.branch == null
                ? null
                : c.branch.getId();

        if (notBlank(req.getProductVoucherCode())) {

            try {

                c.productVoucher = findVoucher(
                        req.getProductVoucherCode(),
                        VoucherApplyTo.PRODUCT,
                        "giảm tiền món"
                );

                c.productDiscount = voucherService.calculateDiscount(
                        c.productVoucher.getCode(),
                        branchId,
                        userId,
                        req.getPaymentMethod(),
                        c.itemsTotal,
                        c.shippingFee
                );

            } catch (BusinessException e) {

                if (strict) {
                    throw e;
                }

                c.productVoucher = null;
                c.productDiscount = BigDecimal.ZERO;
                c.productVoucherError = e.getMessage();
            }
        }

        // 5b. Voucher giảm phí ship
        if (notBlank(req.getShippingVoucherCode())) {

            try {

                if (c.deliveryType == DeliveryType.PICKUP) {
                    throw new BusinessException(
                            "Đơn đến lấy không có phí ship "
                                    + "nên không dùng được mã giảm ship"
                    );
                }

                c.shippingVoucher = findVoucher(
                        req.getShippingVoucherCode(),
                        VoucherApplyTo.SHIPPING,
                        "giảm phí ship"
                );

                c.shippingDiscount = voucherService.calculateDiscount(
                        c.shippingVoucher.getCode(),
                        branchId,
                        userId,
                        req.getPaymentMethod(),
                        c.itemsTotal,
                        c.shippingFee
                );

            } catch (BusinessException e) {

                if (strict) {
                    throw e;
                }

                c.shippingVoucher = null;
                c.shippingDiscount = BigDecimal.ZERO;
                c.shippingVoucherError = e.getMessage();
            }
        }

        // 6. Giảm giá theo hạng thành viên
        MemberTier tier = memberService.getCurrentTier(userId);

        c.memberTierName = tier.getName();

        c.memberDiscount = memberService.calculateDiscount(
                tier,
                c.itemsTotal.subtract(c.productDiscount)
        );

        // 7. Tổng tiền
        c.totalAmount = c.itemsTotal
                .subtract(c.productDiscount)
                .subtract(c.memberDiscount)
                .add(c.shippingFee)
                .subtract(c.shippingDiscount);

        return c;
    }

    /**
     * Hoa hồng app =
     * (tiền món - giảm mã món - giảm hạng thành viên)
     * x tỷ lệ chiết khấu của chi nhánh.
     */
    private BigDecimal commission(Calc c) {

        BigDecimal rate = c.branch.getCommissionRate() == null
                ? BigDecimal.ZERO
                : c.branch.getCommissionRate();

        return c.itemsTotal
                .subtract(c.productDiscount)
                .subtract(c.memberDiscount)
                .multiply(rate)
                .divide(
                        BigDecimal.valueOf(100),
                        0,
                        RoundingMode.HALF_UP
                );
    }

    private Voucher findVoucher(
            String code,
            VoucherApplyTo slot,
            String slotName) {

        String clean = code.trim().toUpperCase();

        Voucher v = voucherRepository.findByCodeIgnoreCase(clean)
                .orElseThrow(() ->
                        new BusinessException(
                                "Mã " + clean + " không tồn tại"
                        )
                );

        if (v.getApplyTo() != slot) {
            throw new BusinessException(
                    "Mã " + v.getCode() + " không phải mã " + slotName
            );
        }

        return v;
    }

    private void fail(Calc c, boolean strict, String message) {

        if (strict) {
            throw new BusinessException(message);
        }

        c.errors.add(message);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** 46000 -> "46.000đ" */
    private static String money(BigDecimal v) {
        return String.format(Locale.US, "%,d", v.longValue())
                .replace(',', '.') + "đ";
    }

    private static String paymentLabel(PaymentMethod m) {
        return switch (m) {
            case COD -> "COD";
            case VNPAY -> "VNPAY";
            case MOMO -> "MoMo";
        };
    }
}