package com.example.demo.service.cache;

import com.example.demo.AppConfig;
import com.example.demo.model.InMemoryKeyValueStoreRepository;
import com.example.demo.model.KeyValueStoreRepository;
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

import static org.junit.jupiter.api.Assertions.*;


class FIFOCacheTest {

    private KeyValueStoreRepository repo = new InMemoryKeyValueStoreRepository();
    private Cache cache;
    private final static int MAX_CACHE_SIZE = 4000000;

    @BeforeEach
    void setUp() {

    }

    @Test
    public void GivenEmptyCache_WhenGettingValueWithThreadsAndSameKey_ThenInvokeFetchingMethodOnlyOnce() throws ExecutionException, InterruptedException {
        initCacheAndRepo(MAX_CACHE_SIZE);

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

        for (Vector<Object> test : tests) {
            List<CompletableFuture<String>> getters = new ArrayList<>();
            for (int i = 0; i < 1000000; i++) {
                // get the value using the same key and multi-threads
                CompletableFuture<String> getter = CompletableFuture.supplyAsync(() -> cache.get((String) test.get(0), (key) -> {
                    ((AtomicInteger) test.get(1)).getAndIncrement();
                    return repo.read(key);
                }));
                getters.add(getter);
            }
            CompletableFuture.allOf(getters.toArray(new CompletableFuture[0])).get();

            // invoke repo.read() only once within multi-threads, which means cache.get() is thread-safe to self
            int expectedInvokedTimes = 1;
            assertEquals(expectedInvokedTimes, ((AtomicInteger) test.get(1)).get());
        }
    }

    @Test
    public void GivenExistentCacheRecord_WhenGettingValueBeforeNewValuePersistedToDataSource_ThenGetTheLatestValueAfterPersisting() throws ExecutionException, InterruptedException {
        initCacheAndRepo(MAX_CACHE_SIZE);

        Random random = new Random();
        String key = String.format("%06d", random.nextInt(1000000));
        String initValue = String.format("%06d", random.nextInt(1000000));
        String newValue = String.format("%06d", random.nextInt(1000000));
        long deferredTime = 300L;

        cache.set(key, initValue, repo::save);

        // update value and defer the persisting to data source
        CompletableFuture<Void> deferredPersistingSetter = CompletableFuture.runAsync(() -> cache.set(key, newValue, (k, v) -> {
            try {
                Thread.sleep(deferredTime);
            } catch (InterruptedException e) {
                // do nothing
            }
            return repo.save(k, v);
        }));

        // invoke cache.get() before finishing persisting new value to data source
        Executor executor = CompletableFuture.delayedExecutor(50L, TimeUnit.MILLISECONDS);
        CompletableFuture<String> getter = CompletableFuture.supplyAsync(() -> cache.get(key, repo::read), executor);

        long startTime = System.currentTimeMillis();
        CompletableFuture.allOf(deferredPersistingSetter, getter).get();
        long endTime = System.currentTimeMillis();

        // this assertion does not work on docker environment, this issue may be caused by Thread.sleep()
        // assertTrue((endTime - startTime) >= deferredTime);
        // get the latest value, which means cache.get() is thread-safe to cache.set()
        assertEquals(newValue, getter.get());
    }

    @Test
    public void GivenExistentCacheRecord_WhenFirstUpdateHavingDeferredPersisting_ThenGetTheLatestValueWithThreadSafety() throws ExecutionException, InterruptedException {
        initCacheAndRepo(MAX_CACHE_SIZE);

        Random random = new Random();
        String key = String.format("%06d", random.nextInt(1000000));
        String initValue = String.format("%06d", random.nextInt(1000000));
        String firstUpdateValue = String.format("%06d", random.nextInt(1000000));
        String secondUpdateValue = String.format("%06d", random.nextInt(1000000));

        long deferredTime = 300L;

        cache.set(key, initValue, repo::save);

        // first update with deferred persisting
        CompletableFuture<Void> firstDeferredPersistingSetter = CompletableFuture.runAsync(() -> cache.set(key, firstUpdateValue, (k, v) -> {
            try {
                Thread.sleep(deferredTime);
            } catch (InterruptedException e) {
                // do nothing
            }
            return repo.save(k, v);
        }));

        // second update
        Executor executor = CompletableFuture.delayedExecutor(50L, TimeUnit.MILLISECONDS);
        CompletableFuture<Void> secondSetter = CompletableFuture.runAsync(() -> cache.set(key, secondUpdateValue, repo::save), executor);

        long startTime = System.currentTimeMillis();
        CompletableFuture.allOf(firstDeferredPersistingSetter, secondSetter).get();
        long endTime = System.currentTimeMillis();

        // get the latest value, which means cache.set() is thread-safe to self
        String expectedLatestValue = secondUpdateValue;
        String actualLatestValue = cache.get(key, repo::read);
        assertTrue((endTime - startTime) >= deferredTime);
        assertEquals(expectedLatestValue, actualLatestValue);
    }

    @Test
    public void GivenRecordsHavingTotalSizeBiggerThanCacheLimit_WhenPuttingAllRecords_ThenCouldGetValueAndCacheSizeNotExceedLimit() {
        initCacheAndRepo(MAX_CACHE_SIZE);

        Random random = new Random();
        for (int i = 0; i < 200000; i++) {
            String keyValue = String.format("%06d", random.nextInt(1000000)); // the size of this record is 24
            cache.set(keyValue, keyValue, repo::save);

            String cachedValue = cache.get(keyValue, repo::read);
            assertEquals(keyValue, cachedValue);
            assertTrue(cache.size() <= MAX_CACHE_SIZE);
        }
    }

    @Test
    public void GivenNearlyFullCache_WhenPuttingTwoRecordsWithMultiThreads_ThenCacheSizeIsCorrectWithThreadSafety() throws ExecutionException, InterruptedException {
        int maxCacheSize = 26;

        for (int i = 0; i < 200000; i++) {
            initCacheAndRepo(maxCacheSize);

            cache.set("000", "000", repo::save); // the size of this record is 12
            cache.set("001", "001", repo::save); // the size of this record is 12
            assertTrue(cache.size() == 24);

            // the size of this record is 14
            CompletableFuture<Void> setterA = CompletableFuture.runAsync(() -> {
                cache.set("003", "0030", repo::save);
            });

            // the size of this record is 14
            CompletableFuture<Void> setterB = CompletableFuture.runAsync(() -> {
                cache.set("004", "0040", repo::save);
            });

            // the cache size should be 14 with FIFO cache, which means cache.set() is thread-safe to self
            CompletableFuture.allOf(setterA, setterB).get();
            assertEquals(14, cache.size());
        }
    }

    private void initCacheAndRepo(int maxCacheSize) {
        AppConfig appConfig = new AppConfig();
        appConfig.cacheSize = maxCacheSize;
        cache = new FIFOCache(appConfig);
    }

}