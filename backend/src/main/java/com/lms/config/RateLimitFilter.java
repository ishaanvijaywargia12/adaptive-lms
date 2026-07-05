package com.lms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_REQUESTS_PER_MINUTE = 20;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
            
        String path = request.getRequestURI();
        
        // Only rate limit sensitive endpoints
        if (isSensitiveEndpoint(path) && request.getMethod().equalsIgnoreCase("POST")) {
            String clientIp = getClientIP(request);
            String userId = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : clientIp;
            String key = "rate_limit:" + userId;
            
            try {
                Long count = redisTemplate.opsForValue().increment(key);
                if (count != null && count == 1) {
                    redisTemplate.expire(key, 1, TimeUnit.MINUTES);
                }
                if (count != null && count > MAX_REQUESTS_PER_MINUTE) {
                    log.warn("Rate limit exceeded for user/IP: {}", userId);
                    sendErrorResponse(response);
                    return;
                }
            } catch (Exception e) {
                // Ignore Redis errors to not block the request
                log.error("Redis rate limiting error: {}", e.getMessage());
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    private boolean isSensitiveEndpoint(String path) {
        return path.contains("/api/auth/login") || 
               path.contains("/submit") || 
               path.contains("/upload-url");
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
    
    private void sendErrorResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        ApiResponse<Void> apiResponse = ApiResponse.error("Too many requests. Please try again later.");
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
