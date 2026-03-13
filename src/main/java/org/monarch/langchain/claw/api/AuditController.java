package org.monarch.langchain.claw.api;

import java.util.List;
import org.monarch.langchain.claw.audit.ToolCallAuditService;
import org.monarch.langchain.claw.audit.ToolCallAuditView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final ToolCallAuditService toolCallAuditService;

    public AuditController(ToolCallAuditService toolCallAuditService) {
        this.toolCallAuditService = toolCallAuditService;
    }

    @GetMapping("/tool-calls")
    public List<ToolCallAuditView> listToolCalls(@RequestHeader("X-User-Id") String userId,
                                                 @RequestParam String sessionId,
                                                 @RequestParam(defaultValue = "20") int limit) {
        return toolCallAuditService.listRecent(userId, sessionId, limit);
    }
}
