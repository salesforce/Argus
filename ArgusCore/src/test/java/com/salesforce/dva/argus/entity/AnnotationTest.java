package com.salesforce.dva.argus.entity;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AnnotationTest {

    @Test
    public void testGetSizeBytes() throws Exception {
        Annotation a = new Annotation("source",
                "id",
                "type",
                "scope",
                "metric",
                System.currentTimeMillis());
        Random r = new Random();
        int expectedSize = 0;
        for (Field f : ArrayUtils.addAll(a.getClass().getDeclaredFields(), // Annotation class
                a.getClass().getSuperclass().getDeclaredFields())) { // TSDBEntity class
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            Class t = f.getType();
            f.setAccessible(true);
            if (t.equals(String.class)) {
                String value = RandomStringUtils.random(r.nextInt(100));
                f.set(a, value);
                expectedSize += value.length();
            } else if (t.equals(Long.class)) {
                Long value = r.nextLong();
                f.set(a, value);
                expectedSize += Long.BYTES;
            } else if (t.equals(int.class)) {
                expectedSize += Integer.BYTES;
            } else if (t.equals(Map.class)) {
                Map<String, String> map = new HashMap<>();
                for (int i = 0; i < r.nextInt(5); i++) {
                    String key = RandomStringUtils.random(r.nextInt(20));
                    String value = RandomStringUtils.random(r.nextInt(20));
                    map.put(key, value);
                    expectedSize += key.length() + value.length();
                }
                f.set(a, map);
            } else {
                if (f.getName().startsWith("$jacoco")) {
                    // jacoco fields start with $, we want to ignore those
                } else {
                    fail(String.format("Unsupported type=%s for field=%s, please update this test", t, f.getName()));
                }
            }
            System.out.println(String.format("field=%s, size=%d", f.getName(), expectedSize));
        }

        int size = a.computeSizeBytes();
        assertEquals(expectedSize, size);
    }
}
