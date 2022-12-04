package com.example.demo.model;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryKeyValueStoreRepository implements KeyValueStoreRepository {
    private Map<String, String> inMemoryStore = new ConcurrentHashMap<>();

    @Override
    public String read(String key) {
        return inMemoryStore.get(key);
    }

    @Override
    public String save(String key, String value) {
        inMemoryStore.put(key, value);
        return inMemoryStore.get(key);
    }
}
