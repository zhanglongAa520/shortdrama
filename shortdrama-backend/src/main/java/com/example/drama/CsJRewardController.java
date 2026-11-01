package com.example.drama;

import com.example.drama.CsJRewardDto.AttemptResponse;
import com.example.drama.CsJRewardDto.AttemptStatusResponse;
import com.example.drama.CsJRewardDto.CallbackResponse;
import com.example.drama.CsJRewardDto.ConfigResponse;
import com.example.drama.CsJRewardDto.CreateAttemptRequest;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rewards/csj")
public class CsJRewardController {
    private final CsJRewardService service;

    public CsJRewardController(CsJRewardService service) { this.service = service; }

    @GetMapping("/config")
    public ConfigResponse config() { return service.config(); }

    @PostMapping("/attempts")
    public AttemptResponse create(@Valid @RequestBody CreateAttemptRequest request, Authentication authentication) {
        return service.createAttempt(authentication.getName(), request.episodeId(), request.platform());
    }

    @GetMapping("/attempts/{attemptId}")
    public AttemptStatusResponse status(@PathVariable String attemptId, Authentication authentication) {
        return service.status(authentication.getName(), attemptId);
    }

    @GetMapping("/callback")
    public CallbackResponse callback(
            @RequestParam(name = "user_id", required = false) String userId,
            @RequestParam(name = "trans_id", required = false) String transactionId,
            @RequestParam(name = "reward_name", required = false) String rewardName,
            @RequestParam(name = "reward_amount", required = false) String rewardAmount,
            @RequestParam(required = false) String extra,
            @RequestParam(required = false) String sign) {
        try {
            return new CallbackResponse(service.callback(
                    userId, transactionId, rewardName, rewardAmount, extra, sign));
        } catch (DataIntegrityViolationException exception) {
            return new CallbackResponse(false);
        }
    }
}
