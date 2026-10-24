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
import vn.utetra.service.ToppingService;
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

    private static final String PHONE_REGEX = "0\\d{9}";

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    public static final int BASE_KM = 3;
    public static final int MAX_KM = 15;
    public static final String DEFAULT_CARRIER_NAME = "Shipper UTETra";

    private final CartService cartService;
    private final VoucherService voucherService;
    private final MemberService memberService;
    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final BranchProductRepository branchProductRepository;
    private final AddressRepository addressRepository;
    private final vn.utetra.service.ShippingFeeService shippingFeeService;
    private final VoucherRepository voucherRepository;
    private final ProductRepository productRepository;
    private final ToppingRepository toppingRepository;
    private final UserRepository userRepository;
    private final OrderNotifier orderNotifier;
    private final ToppingService toppingService;

    public OrderServiceImpl(
            CartService cartService,
            VoucherService voucherService,
            MemberService memberService,
            OrderRepository orderRepository,
            BranchRepository branchRepository,
            BranchProductRepository branchProductRepository,
            AddressRepository addressRepository,
            vn.utetra.service.ShippingFeeService shippingFeeService,
            VoucherRepository voucherRepository,
            ProductRepository productRepository,
            ToppingRepository toppingRepository,
            UserRepository userRepository,
            OrderNotifier orderNotifier,
            ToppingService toppingService) {

        this.cartService = cartService;
        this.voucherService = voucherService;
        this.memberService = memberService;
        this.orderRepository = orderRepository;
        this.branchRepository = branchRepository;
        this.branchProductRepository = branchProductRepository;
        this.addressRepository = addressRepository;
        this.shippingFeeService = shippingFeeService;
        this.voucherRepository = voucherRepository;
        this.productRepository = productRepository;
        this.toppingRepository = toppingRepository;
        this.userRepository = userRepository;
        this.orderNotifier = orderNotifier;
        this.toppingService = toppingService;
    }

    private static class Calc {
        CartRes cart;
        Branch branch;
        DeliveryType deliveryType;
        Address address;
        Carrier carrier;
        BigDecimal distanceKm;

        String receiverName;
        String receiverPhone;

        Voucher productVoucher;

        BigDecimal itemsTotal = BigDecimal.ZERO;
        BigDecimal productDiscount = BigDecimal.ZERO;
        BigDecimal memberDiscount = BigDecimal.ZERO;
        String memberTierName;

        BigDecimal shippingFee = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        List<String> unavailable = new ArrayList<>();
        java.util.Map<Integer, String> itemWarnings = new java.util.LinkedHashMap<>();
        List<String> toppingOut = new ArrayList<>();
        Integer etaMinutes;
        List<String> stopped = new ArrayList<>();
        boolean stockBlocked;

        String productVoucherError;

        List<String> errors = new ArrayList<>();
    }

    @Override
    public CheckoutPreviewRes preview(Integer userId, CheckoutReq req) {

        Calc c = calculate(userId, req, false);

        boolean canOrder = c.errors.isEmpty()
                && !c.stockBlocked
                && c.productVoucherError == null;

        String carrierLabel = c.deliveryType == DeliveryType.DELIVERY
                ? (c.carrier != null ? c.carrier.getName() : DEFAULT_CARRIER_NAME)
                : null;

        return new CheckoutPreviewRes(
                c.cart.items(),
                c.cart.totalQty(),
                c.itemsTotal,
                c.productDiscount,
                c.memberDiscount,
                c.memberTierName,
                c.shippingFee,
                c.totalAmount,
                c.unavailable,
                c.stopped,
                c.itemWarnings,
                c.productVoucherError,
                c.errors,
                canOrder,
                c.distanceKm,
                carrierLabel,
                c.etaMinutes
        );
    }

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

            if (v.getAudience() == VoucherAudience.SPECIFIC
                    && v.getAssignedUsers().stream().noneMatch(u -> u.getId().equals(userId))) {
                continue;
            }

            String reason = null;
            BigDecimal discount = BigDecimal.ZERO;

            if (c.itemsTotal.compareTo(v.getMinOrderValue()) < 0) {

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
                            c.itemsTotal);
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

        if (v.getAudience() == VoucherAudience.SPECIFIC) {
            tags.add("Tặng riêng bạn");
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

        String carrierName = o.getDeliveryType() == DeliveryType.DELIVERY
                ? (o.getCarrier() != null ? o.getCarrier().getName() : DEFAULT_CARRIER_NAME)
                : null;

        return new OrderSummaryRes(
                o.getId(),
                o.getBranch().getName(),
                o.getBranch().getAddress(),
                o.getBranch().getPhone(),
                o.getDeliveryType(),
                o.getReceiverName(),
                o.getReceiverPhone(),
                o.getDeliveryAddress(),
                carrierName,
                o.getTotalAmount(),
                o.getPaymentMethod(),
                o.getStatus(),
                qty,
                o.getCreatedAt().format(DATE_TIME)
        );
    }

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

        Order o = new Order();

        o.setUser(user);
        o.setBranch(c.branch);
        o.setDeliveryType(c.deliveryType);
        o.setCarrier(c.carrier);
        o.setProductVoucher(c.productVoucher);

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
        o.setTotalAmount(c.totalAmount);
        o.setEtaMinutes(c.etaMinutes);

        boolean online = req.getPaymentMethod() == PaymentMethod.VNPAY
                || req.getPaymentMethod() == PaymentMethod.MOMO;
        o.setStatus(online ? OrderStatus.AWAITING_PAYMENT : OrderStatus.PENDING);
        o.setPaymentMethod(req.getPaymentMethod());
        o.setPaymentStatus(PaymentStatus.UNPAID);

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

        Order saved = orderRepository.save(o);

        markVouchersUsed(c, userId, saved.getId());
        cartService.clear(userId);
        if (!online) {
            orderNotifier.onNewOrder(saved);
        }

        return saved;
    }

    private void markVouchersUsed(Calc c, Integer userId, Integer orderId) {

        if (c.productVoucher != null) {
            voucherService.markUsed(
                    c.productVoucher.getId(),
                    userId,
                    orderId
            );
        }
    }

    private Calc calculate(Integer userId, CheckoutReq req, boolean strict) {

        Calc c = new Calc();

        c.cart = cartService.getCart(userId);

        if (c.cart.items().isEmpty()) {
            fail(c, strict, "Giỏ hàng đang trống");
        }

        c.itemsTotal = c.cart.subtotal();

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

            } else if (!c.branch.isOpenAt(java.time.LocalTime.now())) {

                fail(
                        c,
                        strict,
                        "Chi nhánh " + c.branch.getName() + " đã đóng cửa (giờ mở cửa "
                                + c.branch.getHoursLabel() + "), mở lại lúc "
                                + c.branch.getOpenTime().getHour() + ":"
                                + String.format("%02d", c.branch.getOpenTime().getMinute())
                                + ". Bạn vui lòng quay lại sau"
                );
            }
        }

        Set<Integer> hetOChiNhanh = c.branch == null
                ? Set.of()
                : new HashSet<>(
                        branchProductRepository.findUnavailableProductIds(
                                c.branch.getId()
                        )
                );

        Set<Integer> hetToppingOChiNhanh = c.branch == null
                ? Set.of()
                : toppingService.unavailableToppingIds(c.branch.getId());

        String taiDau = c.branch == null
                ? ""
                : " tại " + c.branch.getName();

        for (CartItemRes line : c.cart.items()) {

            Product p = productRepository.findById(line.productId()).orElse(null);
            Topping tp = p == null ? null : p.getTopping();

            boolean ngungBan = p == null
                    || !Boolean.TRUE.equals(p.getIsAvailable())
                    || (tp != null && !Boolean.TRUE.equals(tp.getIsAvailable()));
            if (ngungBan) {
                if (!c.stopped.contains(line.productName())) {
                    c.stopped.add(line.productName());
                }
                continue;
            }

            boolean het = hetOChiNhanh.contains(line.productId())
                    || (tp != null && hetToppingOChiNhanh.contains(tp.getId()));
            if (het) {
                if (!c.unavailable.contains(line.productName())) {
                    c.unavailable.add(line.productName());
                }
                continue;
            }

            List<String> ngungTopping = new ArrayList<>();
            List<String> hetTopping = new ArrayList<>();
            for (CartItemRes.ToppingRes t : line.toppings()) {
                boolean conTopping = toppingRepository.findById(t.id())
                        .map(x -> Boolean.TRUE.equals(x.getIsAvailable()))
                        .orElse(false);
                if (!conTopping) {
                    ngungTopping.add(t.name());
                } else if (hetToppingOChiNhanh.contains(t.id())) {
                    hetTopping.add(t.name());
                }
            }
            List<String> canhBao = new ArrayList<>();
            if (!ngungTopping.isEmpty()) {
                canhBao.add("Topping " + String.join(", ", ngungTopping) + " đã ngừng bán");
            }
            if (!hetTopping.isEmpty()) {
                canhBao.add("Hết topping " + String.join(", ", hetTopping) + " tại chi nhánh này");
            }
            if (!canhBao.isEmpty()) {
                c.itemWarnings.put(line.id(), String.join(" · ", canhBao));
                java.util.stream.Stream.concat(ngungTopping.stream(), hetTopping.stream())
                        .filter(n -> !c.toppingOut.contains(n))
                        .forEach(c.toppingOut::add);
            }
        }

        if (!c.stopped.isEmpty()) {
            failStock(
                    c,
                    strict,
                    "Các món sau đã ngừng bán: " + String.join(", ", c.stopped)
                            + ". Vui lòng xoá khỏi giỏ hàng"
            );
        }

        if (!c.unavailable.isEmpty()) {
            failStock(
                    c,
                    strict,
                    "Các món sau đã hết" + taiDau + ": "
                            + String.join(", ", c.unavailable)
                            + ". Vui lòng xoá khỏi giỏ hoặc chọn chi nhánh khác"
            );
        }

        if (!c.toppingOut.isEmpty()) {
            failStock(
                    c,
                    strict,
                    "Topping " + String.join(", ", c.toppingOut) + " không còn" + taiDau
                            + ". Bạn vào giỏ hàng bỏ ly có topping này rồi thêm lại với topping khác"
            );
        }

        c.deliveryType = req.getDeliveryType() == null
                ? DeliveryType.DELIVERY
                : req.getDeliveryType();

        if (c.deliveryType == DeliveryType.DELIVERY) {

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

            if (c.address != null && c.branch != null) {
                deliveryFee(c, strict);
            }

        } else {

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

        if (req.getPaymentMethod() == null) {

            fail(c, strict, "Vui lòng chọn phương thức thanh toán");

        } else if (
                req.getPaymentMethod() != PaymentMethod.COD
                        && req.getPaymentMethod() != PaymentMethod.VNPAY
                        && req.getPaymentMethod() != PaymentMethod.MOMO
        ) {

            fail(c, strict, "Phương thức thanh toán không hợp lệ");
        }

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
                        c.itemsTotal);

            } catch (BusinessException e) {

                if (strict) {
                    throw e;
                }

                c.productVoucher = null;
                c.productDiscount = BigDecimal.ZERO;
                c.productVoucherError = e.getMessage();
            }
        }

        MemberTier tier = memberService.getCurrentTier(userId);

        c.memberTierName = tier.getName();

        c.memberDiscount = memberService.calculateDiscount(
                tier,
                c.itemsTotal.subtract(c.productDiscount)
        );

        c.totalAmount = c.itemsTotal
                .subtract(c.productDiscount)
                .subtract(c.memberDiscount)
                .add(c.shippingFee);

        c.etaMinutes = c.branch == null || c.cart.items().isEmpty() ? null : eta(c);

        return c;
    }

    private int eta(Calc c) {
        int prep = Math.min(20, 8 + c.cart.totalQty());
        long dangLam = orderRepository.countByBranch_IdAndStatusIn(c.branch.getId(),
                List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED));
        int queue = (int) Math.min(20, dangLam * 2);
        int ship = c.deliveryType == DeliveryType.DELIVERY && c.distanceKm != null
                ? 5 + (int) Math.ceil(c.distanceKm.doubleValue() * 4)
                : 0;
        return prep + queue + ship;
    }

    private void deliveryFee(Calc c, boolean strict) {
        Address a = c.address;
        Branch b = c.branch;
        if (a.getLatitude() == null || a.getLongitude() == null) {
            fail(c, strict, "Địa chỉ nhận hàng chưa ghim vị trí trên bản đồ, vui lòng sửa địa chỉ để tính phí giao");
            return;
        }
        if (b.getLatitude() == null || b.getLongitude() == null) {
            fail(c, strict, "Chi nhánh " + b.getName() + " chưa có vị trí, vui lòng chọn chi nhánh khác");
            return;
        }
        double km = distanceKm(b.getLatitude().doubleValue(), b.getLongitude().doubleValue(),
                a.getLatitude().doubleValue(), a.getLongitude().doubleValue());
        c.distanceKm = BigDecimal.valueOf(km).setScale(1, RoundingMode.HALF_UP);
        if (c.distanceKm.doubleValue() > MAX_KM) {
            fail(c, strict, "Địa chỉ cách " + b.getName() + " " + c.distanceKm + " km, quán chỉ giao trong "
                    + MAX_KM + " km. Vui lòng chọn chi nhánh gần hơn hoặc đến lấy tại quán");
            return;
        }
        c.shippingFee = shippingFeeService.feeFor(c.distanceKm);
        c.carrier = shippingFeeService.shipper();
    }

    static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * 6371 * Math.asin(Math.sqrt(h));
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

    private void failStock(Calc c, boolean strict, String message) {

        if (strict) {
            throw new BusinessException(message);
        }

        c.stockBlocked = true;
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
