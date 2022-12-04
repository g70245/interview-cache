package com.example.demo.service;

import com.example.demo.model.KeyValueStoreRepository;
import com.example.demo.service.cache.Cache;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class KeyValueStore {
    private final KeyValueStoreRepository keyValueStoreRepository;
    private final Cache cache;

    @Autowired
    public KeyValueStore(KeyValueStoreRepository keyValueStoreRepository, Cache cache) {
        this.keyValueStoreRepository = keyValueStoreRepository;
        this.cache = cache;
    }

    public String get(@NonNull String key) {
        return cache.get(key, keyValueStoreRepository::read);
    }

    public void put(@NonNull String key, @NonNull String value) {
        cache.set(key, value, keyValueStoreRepository::save);
    }
}
