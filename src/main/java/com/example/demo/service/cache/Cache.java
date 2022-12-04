package com.example.demo.service.cache;

import lombok.Getter;
import lombok.Setter;

public interface Cache {
    String get(String key, DataFetcher dataFetcher);

    void set(String key, String value, DataWriter dataWriter);

    int size();

    interface DataFetcher {
        String fetch(String key);
    }

    interface DataWriter {
        String write(String key, String value);
    }

    class Resource {

        @Getter
        @Setter
        private String value;

        public Resource() {
        }

        boolean hasValue() {
            return value != null;
        }
    }
}
