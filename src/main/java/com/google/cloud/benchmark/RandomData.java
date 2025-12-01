package com.google.cloud.benchmark;

import java.util.concurrent.ThreadLocalRandom;

public class RandomData {
    public static byte[] generate(int size) {
        byte[] data = new byte[size];
        ThreadLocalRandom.current().nextBytes(data);
        return data;
    }
}
