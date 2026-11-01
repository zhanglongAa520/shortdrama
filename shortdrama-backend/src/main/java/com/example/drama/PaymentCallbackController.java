package com.example.drama;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/callbacks")
public class PaymentCallbackController {
    private final PaymentGatewayService gateway;
    private final PaymentService payments;
    public PaymentCallbackController(PaymentGatewayService gateway, PaymentService payments) {
        this.gateway = gateway;
        this.payments = payments;
    }

    @PostMapping("/wechat")
    public Map<String, String> wechat(@RequestBody String body,
            @RequestHeader("Wechatpay-Serial") String serial,
            @RequestHeader("Wechatpay-Timestamp") String timestamp,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature) {
        payments.complete(PaymentProvider.WECHAT, gateway.verifyWechat(body, serial, timestamp, nonce, signature));
        return Map.of("code", "SUCCESS", "message", "成功");
    }

    @PostMapping(value = "/alipay", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String alipay(@RequestParam Map<String, String> parameters) {
        payments.complete(PaymentProvider.ALIPAY, gateway.verifyAlipay(parameters));
        return "success";
    }
}
