package org.example.globalratelimiting.sidecar;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimitClientFilter extends OncePerRequestFilter {

    private final AtomicLong requestCounter = new AtomicLong(0);
    private volatile double currentDropRatio = 0.0;

    private final RestTemplate restTemplate = new RestTemplate();
    private final String AGGREGATOR_URL = "http://zone-aggregator-service/api/aggregator";
    private final String ZONE_ID = "us-east-1"; // Typically injected via environment variable

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Enforce based on the cached drop ratio
        if (currentDropRatio > 0.0 && Math.random() < currentDropRatio) {
            response.setStatus(429);
            response.getWriter().write("Rate limit exceeded");
            return;
        }

        requestCounter.incrementAndGet();
        filterChain.doFilter(request, response);
    }

    @Scheduled(fixedRate = 1000)
    public void reportMetricsAndFetchDirectives() {
        long currentCount = requestCounter.getAndSet(0);

        try {
            // Push count to aggregator and get the latest global drop ratio back
            Double newDropRatio = restTemplate.postForObject(
                    AGGREGATOR_URL + "/report?zoneId=" + ZONE_ID + "&count=" + currentCount,
                    null,
                    Double.class
            );

            if (newDropRatio != null) {
                this.currentDropRatio = newDropRatio;
            }
        } catch (Exception e) {
            System.err.println("Failed to communicate with Aggregator");
        }
    }
}
