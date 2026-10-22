package vn.utetra.controller.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.entity.Order;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.repository.OrderRepository;

@Controller
@RequestMapping("/admin/reports")
@Transactional(readOnly = true)
public class AdminReportController {

	private final OrderRepository orderRepository;

	public AdminReportController(OrderRepository orderRepository) {
		this.orderRepository = orderRepository;
	}

	@GetMapping
    public String index(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            Model model) {

        LocalDate today = LocalDate.now();
        LocalDate firstDayOfMonth = today.withDayOfMonth(1);

        // Mặc định hiển thị báo cáo từ đầu tháng đến hôm nay.
        if (startDate == null) {
            startDate = firstDayOfMonth;
        }
        if (endDate == null) {
            endDate = today;
        }

        // Nếu người dùng chọn ngày ngược nhau, đổi lại cho hợp lệ.
        if (startDate.isAfter(endDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }

        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        LocalDateTime monthStart = firstDayOfMonth.atStartOfDay();
        LocalDateTime nextMonthStart =
                firstDayOfMonth.plusMonths(1).atStartOfDay();

        LocalDateTime rangeStart = startDate.atStartOfDay();
        // Dùng mốc đầu ngày kế tiếp để bao gồm toàn bộ ngày kết thúc.
        LocalDateTime rangeEnd = endDate.plusDays(1).atStartOfDay();

        OrderStatus delivered = OrderStatus.DELIVERED;

        BigDecimal todayRevenue =
                orderRepository.sumRevenueByStatusAndDeliveredAt(
                        delivered, todayStart, tomorrowStart);

        BigDecimal monthRevenue =
                orderRepository.sumRevenueByStatusAndDeliveredAt(
                        delivered, monthStart, nextMonthStart);

        BigDecimal selectedRevenue =
                orderRepository.sumRevenueByStatusAndDeliveredAt(
                        delivered, rangeStart, rangeEnd);

        long todayOrders =
                orderRepository
                        .countByStatusAndDeliveredAtGreaterThanEqualAndDeliveredAtLessThan(
                                delivered, todayStart, tomorrowStart);

        long monthOrders =
                orderRepository
                        .countByStatusAndDeliveredAtGreaterThanEqualAndDeliveredAtLessThan(
                                delivered, monthStart, nextMonthStart);

        long selectedOrders =
                orderRepository
                        .countByStatusAndDeliveredAtGreaterThanEqualAndDeliveredAtLessThan(
                                delivered, rangeStart, rangeEnd);

        List<Order> orders = orderRepository
                .findByStatusAndDeliveredAtGreaterThanEqualAndDeliveredAtLessThanOrderByDeliveredAtAsc(
                        delivered, rangeStart, rangeEnd);

        // Gom doanh thu theo ngày.
        Map<LocalDate, BigDecimal> revenueByDay = new LinkedHashMap<>();

        // Khởi tạo đủ các ngày, kể cả ngày không có doanh thu.
        for (LocalDate date = startDate;
                !date.isAfter(endDate);
                date = date.plusDays(1)) {
            revenueByDay.put(date, BigDecimal.ZERO);
        }

        for (Order order : orders) {
            if (order.getDeliveredAt() == null) {
                continue;
            }

            LocalDate date = order.getDeliveredAt().toLocalDate();
            BigDecimal amount = order.getSalesAmount() != null
                    ? order.getSalesAmount()
                    : BigDecimal.ZERO;

            revenueByDay.merge(date, amount, BigDecimal::add);
        }

        DateTimeFormatter chartDateFormat =
                DateTimeFormatter.ofPattern("dd/MM");

        List<String> chartLabels = revenueByDay.keySet().stream()
                .map(date -> date.format(chartDateFormat))
                .toList();

        List<BigDecimal> chartValues =
                new ArrayList<>(revenueByDay.values());

        // Gom doanh thu, số đơn theo chi nhánh.
        Map<Integer, Map<String, Object>> branchMap =
                new LinkedHashMap<>();

        for (Order order : orders) {
            if (order.getBranch() == null
                    || order.getBranch().getId() == null) {
                continue;
            }

            Integer branchId = order.getBranch().getId();

            Map<String, Object> report = branchMap.computeIfAbsent(
                    branchId, id -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("branchId", id);
                        item.put("branchName",
                                order.getBranch().getName() != null
                                        ? order.getBranch().getName()
                                        : "Chi nhánh #" + id);
                        item.put("orderCount", 0L);
                        item.put("revenue", BigDecimal.ZERO);
                        return item;
                    });

            report.put("orderCount",
                    (Long) report.get("orderCount") + 1L);

            BigDecimal amount = order.getSalesAmount() != null
                    ? order.getSalesAmount()
                    : BigDecimal.ZERO;

            report.put("revenue",
                    ((BigDecimal) report.get("revenue")).add(amount));

        }

        // Trung bình mỗi đơn của từng chi nhánh
        branchMap.values().forEach(item -> item.put("average",
                average((BigDecimal) item.get("revenue"), (Long) item.get("orderCount"))));

        List<Map<String, Object>> branchReports =
                branchMap.values().stream()
                        .sorted(Comparator.comparing(
                                item -> (BigDecimal) item.get("revenue"),
                                Comparator.reverseOrder()))
                        .collect(Collectors.toList());

        model.addAttribute("todayRevenue", todayRevenue);
        model.addAttribute("monthRevenue", monthRevenue);
        model.addAttribute("selectedRevenue", selectedRevenue);
        model.addAttribute("selectedAverage", average(selectedRevenue, selectedOrders));

        model.addAttribute("todayOrders", todayOrders);
        model.addAttribute("monthOrders", monthOrders);
        model.addAttribute("selectedOrders", selectedOrders);

        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartValues", chartValues);
        model.addAttribute("branchReports", branchReports);

        return "admin/reports/index";
    }

    /** Giá trị trung bình 1 đơn = doanh thu / số đơn */
    private static BigDecimal average(BigDecimal revenue, long orders) {
        return orders <= 0 || revenue == null ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(orders), 0, java.math.RoundingMode.HALF_UP);
    }
}
