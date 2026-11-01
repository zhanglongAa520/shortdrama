package com.example.drama;

import com.example.drama.AuthDto.CsrfResponse;
import com.example.drama.AuthDto.LoginRequest;
import com.example.drama.AuthDto.RegisterRequest;
import com.example.drama.AuthDto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContexts;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager, SecurityContextRepository securityContexts,
                          SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.securityContexts = securityContexts;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return response(find(authentication.getName()));
    }

    @PostMapping("/register")
    @Transactional
    public UserResponse register(@Valid @RequestBody RegisterRequest input,
                                 HttpServletRequest request, HttpServletResponse response) {
        String username = input.username().trim();
        if (username.equalsIgnoreCase("admin") || users.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名不可用");
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setNickname(input.nickname().trim());
        user.setPasswordHash(passwordEncoder.encode(input.password()));
        user.setRole(UserRole.USER);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名不可用");
        }
        authenticate(username, input.password(), request, response);
        return response(user);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest input,
                              HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticate(input.username(), input.password(), request, response);
        return response(find(authentication.getName()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContext empty = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(empty);
        securityContexts.saveContext(empty, request, response);
        if (request.getSession(false) != null) request.getSession(false).invalidate();
    }

    private Authentication authenticate(String username, String password,
                                        HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContexts.saveContext(context, request, response);
        return authentication;
    }

    private AppUser find(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private UserResponse response(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }
}
