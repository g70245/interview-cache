package com.example.demo.service;

import com.example.demo.AppConfig;
import com.example.demo.model.InMemoryKeyValueStoreRepository;
import com.example.demo.service.cache.FIFOCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeyValueStoreTest {

    private final List<KeyValueStore> keyValueStores = new ArrayList<>();
    private InMemoryKeyValueStoreRepository repo;

    @BeforeEach
    public void setUp() {
        AppConfig appConfig = new AppConfig();
        appConfig.cacheSize = 1000;

        repo = new InMemoryKeyValueStoreRepository();
        keyValueStores.add(new KeyValueStore(repo, new FIFOCache(appConfig)));
        // Add other cache implementations for testing. The tests should have the same results.
    }

    @Test
    public void GivenExistentRecordAndNewValue_WhenGettingValueAfterPuttingRecord_ThenReturnTheLatestValue() {
        keyValueStores.forEach(keyValueStore -> {
            String key = "key";
            String intiValue = "intiValue";
            String newValue = "newValue";

            keyValueStore.put(key, intiValue);
            assertEquals(intiValue, keyValueStore.get(key));

            keyValueStore.put(key, newValue);
            assertEquals(newValue, keyValueStore.get(key));
        });
    }

    @Test
    public void GivenExistentRecordKey_WhenGettingValue_ThenReturnExpectedValue() {
        keyValueStores.forEach(keyValueStore -> {
            String existentRecordKey = "existentRecordKey";
            String existentRecordValue = "existentRecordValue";
            String expectedValue = repo.save(existentRecordKey, existentRecordValue);

            assertEquals(expectedValue, keyValueStore.get(existentRecordKey));
        });
    }

    @Test
    public void GivenNonExistentRecordKey_WhenGettingValue_ThenReturnNull() {
        keyValueStores.forEach(keyValueStore -> {
            String keyHavingNoRecord = "keyHavingNoRecord";
            assertNull(keyValueStore.get(keyHavingNoRecord));
        });
    }

    @Test
    public void GivenNullKey_WhenGettingValue_ThenThrowException() {
        keyValueStores.forEach(
                keyValueStore -> assertThrows(NullPointerException.class, () -> keyValueStore.get(null))
        );
    }

    @Test
    public void GivenNullKeyOrNullValue_WhenPuttingRecord_ThenThrowException() {
        keyValueStores.forEach(keyValueStore -> {
            String notNullKey = "notNullKey";
            String notNullValue = "notNullValue";

            assertThrows(NullPointerException.class, () -> keyValueStore.put(notNullKey, null));
            assertThrows(NullPointerException.class, () -> keyValueStore.put(null, notNullValue));
        });
    }
}