package com.example.demo.service.cache;

import com.example.demo.AppConfig;
import com.example.demo.model.InMemoryKeyValueStoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class FIFOCacheTest {

    private InMemoryKeyValueStoreRepository repo;
    private Cache cache;
    private final static int MAX_CACHE_SIZE = 4000000;

    @BeforeEach
    void setUp() {
        repo = new InMemoryKeyValueStoreRepository();

        AppConfig appConfig = new AppConfig();
        appConfig.cacheSize = MAX_CACHE_SIZE;
        cache = new FIFOCache(appConfig);
    }

    @Test
    public void GivenEmptyCache_WhenGettingValueWithThreadsAndSameKey_ThenInvokeFetchingMethodWithThreadSafety() throws ExecutionException, InterruptedException {
        Random random = new Random();
        List<Vector<Object>> tests = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Vector<Object> test = new Vector<>();
            String keyValue = String.format("%06d", random.nextInt(1000000));
            repo.save(keyValue, keyValue);
            test.add(keyValue);
            test.add(new AtomicInteger(0));
            tests.add(test);
        }

        // check thread-safety
        for (Vector<Object> test : tests) {
            List<CompletableFuture<String>> getters = new ArrayList<>();
            for (int i = 0; i < 1000000; i++) {
                CompletableFuture<String> getter = CompletableFuture.supplyAsync(() -> cache.get((String) test.get(0), (key) -> {
                    ((AtomicInteger) test.get(1)).getAndIncrement();
                    return repo.read(key);
                }));
                getters.add(getter);
            }
            CompletableFuture.allOf(getters.toArray(new CompletableFuture[0])).get();

            int expectedInvokedTimes = 1;
            assertEquals(expectedInvokedTimes, ((AtomicInteger) test.get(1)).get());
        }
    }

    @Test
    public void GivenExistentCacheRecord_WhenGettingValueAfterValueUpdateWithDeferredPersisting_ThenGetTheLatestValueWithThreadSafety() throws ExecutionException, InterruptedException {
        Random random = new Random();
        String key = String.format("%06d", random.nextInt(1000000));
        String initValue = String.format("%06d", random.nextInt(1000000));
        String newValue = String.format("%06d", random.nextInt(1000000));
        long deferredTime = 300L;

        cache.set(key, initValue, repo::save);

        // check thread-safety
        CompletableFuture<Void> deferredPersistingSetter = CompletableFuture.runAsync(() -> cache.set(key, newValue, (k, v) -> {
            try {
                Thread.sleep(deferredTime);
            } catch (InterruptedException e) {
                // do nothing
            }
            return repo.save(k, v);
        }));

        Executor executor = CompletableFuture.delayedExecutor(50L, TimeUnit.MILLISECONDS);
        CompletableFuture<String> getter = CompletableFuture.supplyAsync(() -> cache.get(key, repo::read), executor);

        long startTime = System.currentTimeMillis();
        CompletableFuture.allOf(deferredPersistingSetter, getter).get();
        long endTime = System.currentTimeMillis();

        assertTrue((endTime - startTime) >= deferredTime);
        assertEquals(newValue, getter.get());
    }

    @Test
    public void GivenExistentCacheRecord_WhenFirstUpdateHavingDeferredPersisting_ThenGetTheLatestValueWithThreadSafety() throws ExecutionException, InterruptedException {
        Random random = new Random();
        String key = String.format("%06d", random.nextInt(1000000));
        String initValue = String.format("%06d", random.nextInt(1000000));
        String firstUpdateValue = String.format("%06d", random.nextInt(1000000));
        String secondUpdateValue = String.format("%06d", random.nextInt(1000000));

        long deferredTime = 300L;

        cache.set(key, initValue, repo::save);

        // check thread-safety
        CompletableFuture<Void> firstDeferredPersistingSetter = CompletableFuture.runAsync(() -> cache.set(key, firstUpdateValue, (k, v) -> {
            try {
                Thread.sleep(deferredTime);
            } catch (InterruptedException e) {
                // do nothing
            }
            return repo.save(k, v);
        }));

        Executor executor = CompletableFuture.delayedExecutor(50L, TimeUnit.MILLISECONDS);
        CompletableFuture<Void> secondSetter = CompletableFuture.runAsync(() -> cache.set(key, secondUpdateValue, repo::save), executor);

        long startTime = System.currentTimeMillis();
        CompletableFuture.allOf(firstDeferredPersistingSetter, secondSetter).get();
        long endTime = System.currentTimeMillis();

        String expectedLatestValue = secondUpdateValue;
        String actualLatestValue = cache.get(key, repo::read);
        assertTrue((endTime - startTime) >= deferredTime);
        assertEquals(expectedLatestValue, actualLatestValue);
    }

    @Test
    public void GivenRecordsHavingTotalSizeBiggerThanCacheLimit_WhenPuttingAllRecords_ThenCouldGetValueAndCacheSizeNotExceedLimit() {
        Random random = new Random();
        for (int i = 0; i < 200000; i++) {
            String keyValue = String.format("%06d", random.nextInt(1000000));
            cache.set(keyValue, keyValue, repo::save);

            String cachedValue = cache.get(keyValue, repo::read);
            assertEquals(keyValue, cachedValue);
        }

        assertTrue(cache.size() <= MAX_CACHE_SIZE);
    }
}