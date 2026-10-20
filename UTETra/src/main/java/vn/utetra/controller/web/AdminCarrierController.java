package vn.utetra.controller.web;

import vn.utetra.entity.Carrier;
import vn.utetra.repository.CarrierRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/admin/carriers")
public class AdminCarrierController {

    private final CarrierRepository carrierRepository;

    public AdminCarrierController(CarrierRepository carrierRepository) {
        this.carrierRepository = carrierRepository;
    }

    @GetMapping
    public String index(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            Model model) {

        var carriers = keyword.isBlank()
                ? carrierRepository.findAll()
                : carrierRepository.findByNameContainingIgnoreCaseOrderByIdAsc(
                        keyword.trim());

        if ("ACTIVE".equalsIgnoreCase(status)) {
            carriers = carriers.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                    .toList();
        } else if ("INACTIVE".equalsIgnoreCase(status)) {
            carriers = carriers.stream()
                    .filter(c -> !Boolean.TRUE.equals(c.getIsActive()))
                    .toList();
        }

        model.addAttribute("carriers", carriers);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("totalCarriers", carrierRepository.count());
        model.addAttribute("activeCarriers",
                carrierRepository.findAll().stream()
                        .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                        .count());

        return "admin/carriers/index";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        Carrier carrier = new Carrier();
        carrier.setFee(BigDecimal.ZERO);
        carrier.setIsActive(true);

        model.addAttribute("carrier", carrier);
        model.addAttribute("pageTitle", "Thêm nhà vận chuyển");
        model.addAttribute("isEdit", false);

        return "admin/carriers/form";
    }

    @PostMapping
    public String create(
            @ModelAttribute("carrier") Carrier carrier,
            Model model,
            RedirectAttributes redirectAttributes) {

        String error = validateCarrier(carrier, null);

        if (error != null) {
            model.addAttribute("pageTitle", "Thêm nhà vận chuyển");
            model.addAttribute("isEdit", false);
            model.addAttribute("errorMessage", error);
            return "admin/carriers/form";
        }

        carrier.setId(null);
        carrier.setName(carrier.getName().trim());
        carrier.setIsActive(true);

        carrierRepository.save(carrier);

        redirectAttributes.addFlashAttribute(
                "successMessage", "Thêm nhà vận chuyển thành công.");

        return "redirect:/admin/carriers";
    }

    @GetMapping("/{id}/edit")
    public String editForm(
            @PathVariable Integer id,
            Model model,
            RedirectAttributes redirectAttributes) {

        Carrier carrier = carrierRepository.findById(id).orElse(null);

        if (carrier == null) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Không tìm thấy nhà vận chuyển.");
            return "redirect:/admin/carriers";
        }

        model.addAttribute("carrier", carrier);
        model.addAttribute("pageTitle", "Chỉnh sửa nhà vận chuyển");
        model.addAttribute("isEdit", true);

        return "admin/carriers/form";
    }

    @PostMapping("/{id}/edit")
    public String update(
            @PathVariable Integer id,
            @ModelAttribute("carrier") Carrier formCarrier,
            Model model,
            RedirectAttributes redirectAttributes) {

        Carrier existing = carrierRepository.findById(id).orElse(null);

        if (existing == null) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Không tìm thấy nhà vận chuyển.");
            return "redirect:/admin/carriers";
        }

        String error = validateCarrier(formCarrier, id);

        if (error != null) {
            formCarrier.setId(id);
            model.addAttribute("carrier", formCarrier);
            model.addAttribute("pageTitle", "Chỉnh sửa nhà vận chuyển");
            model.addAttribute("isEdit", true);
            model.addAttribute("errorMessage", error);

            return "admin/carriers/form";
        }

        existing.setName(formCarrier.getName().trim());
        existing.setFee(formCarrier.getFee());

        // Không nhận isActive từ form chỉnh sửa.
        // Trạng thái được quản lý riêng bằng chức năng bật/tắt.
        carrierRepository.save(existing);

        redirectAttributes.addFlashAttribute(
                "successMessage", "Cập nhật nhà vận chuyển thành công.");

        return "redirect:/admin/carriers";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(
            @PathVariable Integer id,
            RedirectAttributes redirectAttributes) {

        Carrier carrier = carrierRepository.findById(id).orElse(null);

        if (carrier == null) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Không tìm thấy nhà vận chuyển.");
            return "redirect:/admin/carriers";
        }

        boolean active = Boolean.TRUE.equals(carrier.getIsActive());
        carrier.setIsActive(!active);

        carrierRepository.save(carrier);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                active
                        ? "Đã tắt nhà vận chuyển."
                        : "Đã bật nhà vận chuyển.");

        return "redirect:/admin/carriers";
    }

    private String validateCarrier(Carrier carrier, Integer excludeId) {
        if (carrier.getName() == null || carrier.getName().isBlank()) {
            return "Tên nhà vận chuyển không được để trống.";
        }

        String name = carrier.getName().trim();

        if (name.length() > 100) {
            return "Tên nhà vận chuyển không được vượt quá 100 ký tự.";
        }

        boolean duplicate = excludeId == null
                ? carrierRepository.existsByNameIgnoreCase(name)
                : carrierRepository.existsByNameIgnoreCaseAndIdNot(
                        name, excludeId);

        if (duplicate) {
            return "Tên nhà vận chuyển đã tồn tại.";
        }

        if (carrier.getFee() == null
                || carrier.getFee().compareTo(BigDecimal.ZERO) < 0) {
            return "Phí vận chuyển phải lớn hơn hoặc bằng 0.";
        }

        return null;
    }
}
