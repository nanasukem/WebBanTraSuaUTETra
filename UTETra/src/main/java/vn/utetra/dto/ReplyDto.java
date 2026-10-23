package vn.utetra.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyDto {
	private String responderName;
	private String responderRole;
	private String content;
	private LocalDateTime createdAt;
}