package com.example.drama;

import com.example.drama.PaymentDto.ConfigResponse;
import com.example.drama.PaymentDto.CreateOrderRequest;
import com.example.drama.PaymentDto.OrderResponse;
import com.example.drama.PaymentDto.ProductResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service) { this.service = service; }

    @GetMapping("/config") public ConfigResponse config() { return service.config(); }
    @GetMapping("/catalog") public List<ProductResponse> catalog() { return service.catalog(); }
    @GetMapping("/orders") public List<OrderResponse> orders(Authentication auth) {
        return service.list(auth.getName());
    }
    @GetMapping("/orders/{id}") public OrderResponse order(@PathVariable String id, Authentication auth) {
        return service.find(auth.getName(), id);
    }
    @PostMapping("/orders") @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request, Authentication auth,
                                HttpServletRequest servletRequest) {
        return service.create(auth.getName(), request, servletRequest.getRemoteAddr());
    }
}
