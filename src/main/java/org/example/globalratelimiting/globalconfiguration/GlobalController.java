package org.example.globalratelimiting.globalconfiguration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class GlobalController {

    private volatile long globalQpsLimit = 10000;
    private final StringRedisTemplate redisTemplate;

    public GlobalController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedRate = 1000)
    public void calculateGlobalUtilization() {
        long totalGlobalQps = 0;

        // 1. Find all zone keys in Redis
        Set<String> zoneKeys = redisTemplate.keys("ratelimit:zone:*");

        if (zoneKeys != null) {
            for (String key : zoneKeys) {
                // Read the count and delete the key atomically (requires Redis 6.2+)
                // For older Redis versions, you can use a Lua script to get-and-delete
                String countStr = redisTemplate.opsForValue().getAndDelete(key);

                if (countStr != null) {
                    totalGlobalQps += Long.parseLong(countStr);
                }
            }
        }

        // 2. Calculate new global drop ratio
        double globalDropRatio = 0.0;
        if (totalGlobalQps > globalQpsLimit) {
            double excess = totalGlobalQps - globalQpsLimit;
            globalDropRatio = excess / (double) totalGlobalQps;
        }

        // 3. Store the updated drop ratio back in Redis for aggregators to read
        redisTemplate.opsForValue().set("ratelimit:global:drop_ratio", String.valueOf(globalDropRatio));

        System.out.println("Global QPS: " + totalGlobalQps + " | Limit: " + globalQpsLimit + " | New Drop Ratio: " + globalDropRatio);
    }

    public void updateGlobalQpsLimit(long newLimit) {
        this.globalQpsLimit = newLimit;
        System.out.println("Updated Global QPS Limit to: " + newLimit);
    }
}
