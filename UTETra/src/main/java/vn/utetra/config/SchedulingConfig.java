package vn.utetra.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật chạy định kỳ (@Scheduled): vd quét voucher sắp hết hạn để báo Admin */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
