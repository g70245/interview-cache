package com.example.demo.service.cache;

import com.example.demo.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FIFOCache implements Cache {

    private final int MAX_CACHE_SIZE;
    private final Queue<String> queue = new ConcurrentLinkedQueue<>();
    private final Map<String, Resource> cache = new ConcurrentHashMap<>();
    private int size = 0;

    @Autowired
    public FIFOCache(AppConfig appConfig) {
        this.MAX_CACHE_SIZE = appConfig.cacheSize;
    }

    @Override
    public String get(String key, DataFetcher dataFetcher) {
        Resource resource;
        synchronized (cache) {
            resource = cache.get(key);
            if (resource == null) {
                resource = new Resource();
                cache.put(key, resource);
            }
        }

        synchronized (resource) {
            if (resource.hasValue()) {
                return resource.getValue();
            }

            // use read-through strategy
            String value = dataFetcher.fetch(key);
            if (value != null) {
                setCache(key, value, resource);
            }
            return value;
        }
    }



    @Override
    public void set(String key, String value, DataWriter dataWriter) {
        Resource resource;
        synchronized (cache) {
            resource = cache.get(key);
            if (resource == null) {
                resource = new Resource();
                cache.put(key, resource);
            }
        }

        synchronized (resource) {
            // use write-through strategy
            if (resource.hasValue()) {
                updateCache(key, value);
            } else {
                setCache(key, value, resource);
            }

            if (cache.containsKey(key) && resource.hasValue()) {
                String persistedValue = dataWriter.write(key, resource.getValue());
                if (persistedValue == null) {
                    removeCache(key);
                }
            }
        }
    }

    @Override
    public int size() {
        return size;
    }

    private void setCache(String key, String value, Resource resource) {
        int newRecordSize = calculateRecordSize(key, value);
        if (newRecordSize > MAX_CACHE_SIZE) {
            cache.remove(key);
            return;
        }

        while (!cache.isEmpty() && doesCacheExceedMaxSize(newRecordSize)) {
            removeHeadCache();
        }

        size += newRecordSize;
        queue.offer(key);
        resource.setValue(value);
    }

    private void updateCache(String key, String newValue) {
        int newRecordSize = calculateRecordSize(key, newValue);
        if (newRecordSize > MAX_CACHE_SIZE) {
            removeCache(key);
            return;
        }

        Resource resource = cache.get(key);
        int difference = calculateSizeDifference(resource.getValue(), newValue);
        while (!cache.isEmpty() && doesCacheExceedMaxSize(difference)) {
            removeHeadCache();
        }

        resource.setValue(newValue);
        if (cache.containsKey(key)) {
            size += difference;
        } else {
            size += newRecordSize;
            cache.put(key, resource);
        }
    }

    private void removeHeadCache() {
        String key = queue.poll();
        if (key != null) {
            Resource resource = cache.remove(key);
            size -= calculateRecordSize(key, resource.getValue());
        }
    }

    private void removeCache(String key) {
        if (queue.remove(key)) {
            Resource resource = cache.remove(key);
            size -= calculateRecordSize(key, resource.getValue());
        }
    }

    private boolean doesCacheExceedMaxSize(int addend) {
        return size + addend > MAX_CACHE_SIZE;
    }

    private int calculateRecordSize(String key, String value) {
        if (value == null) {
            return  key.length() * 2;
        }
        return key.length() * 2 + value.length() * 2;
    }

    private int calculateSizeDifference(String previousValue, String newValue) {
        return newValue.length() * 2 - previousValue.length() * 2;
    }
}
