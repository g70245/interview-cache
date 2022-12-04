package com.example.demo.model;

public interface KeyValueStoreRepository {
    String read(String key);
    String save(String key, String value);
}
