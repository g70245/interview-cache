package com.example.demo;


import com.example.demo.service.cache.Cache;
import com.example.demo.service.cache.FIFOCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Value("${cache.size}")
    public Integer cacheSize;

    // configure the cache policy here
    @Bean
    public Cache cache() {
        return new FIFOCache(this);
    }

}
