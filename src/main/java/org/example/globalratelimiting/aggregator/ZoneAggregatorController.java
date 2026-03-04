package org.example.globalratelimiting.aggregator;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/aggregator")
public class ZoneAggregatorController {

    private final StringRedisTemplate redisTemplate;

    public ZoneAggregatorController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/report")
    public double receiveClientMetrics(@RequestParam String zoneId, @RequestParam long count) {
        // 1. Increment the zone-specific counter in Redis
        String zoneKey = "ratelimit:zone:" + zoneId;
        redisTemplate.opsForValue().increment(zoneKey, count);

        // 2. Fetch the latest global drop ratio computed by the Controller
        String dropRatioStr = redisTemplate.opsForValue().get("ratelimit:global:drop_ratio");

        return dropRatioStr != null ? Double.parseDouble(dropRatioStr) : 0.0;
    }
}
