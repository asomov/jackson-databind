package com.fasterxml.jackson.databind.ser.impl;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.util.TypeKey;

/**
 * Specialized read-only map used for storing and accessing serializers by type.
 * Used for per-{@link com.fasterxml.jackson.databind.ObjectMapper} sharing
 * of resolved serializers; in addition, a per-call non-shared read/write
 * map may be needed, which will (after call) get merged to create a new
 * shared map of this type.
 */
public class JsonSerializerMap
{
    private final Bucket[] _buckets;

    private final int _size;
    
    public JsonSerializerMap(Map<TypeKey,JsonSerializer<Object>> serializers)
    {
        int size = findSize(serializers.size());
        _size = size;
        int hashMask = (size-1);
        Bucket[] buckets = new Bucket[size];
        for (Map.Entry<TypeKey,JsonSerializer<Object>> entry : serializers.entrySet()) {
            TypeKey key = entry.getKey();
            int index = key.hashCode() & hashMask;
            buckets[index] = new Bucket(buckets[index], key, entry.getValue());
        }
        _buckets = buckets;
    }
    
    private final static int findSize(int size)
    {
        // For small enough results (64 or less), we'll require <= 50% fill rate; otherwise 80%
        int needed = (size <= 64) ? (size + size) : (size + (size >> 2));
        int result = 8;
        while (result < needed) {
            result += result;
        }
        return result;
    }

    /*
    /**********************************************************
    /* Public API
    /**********************************************************
     */

    public int size() { return _size; }
    
    public JsonSerializer<Object> find(TypeKey key)
    {
        Bucket bucket = _buckets[key.hashCode(_buckets.length)];
        /* Ok let's actually try unrolling loop slightly as this shows up in profiler;
         * and also because in vast majority of cases first entry is either null
         * or matches.
         */
        if ((bucket != null) && bucket.key.equals(key)) {
            return bucket.value;
        }
        return _find(key, bucket);
    }
    
    private final JsonSerializer<Object> _find(TypeKey key, Bucket bucket) {
        if (bucket == null) {
            return null;
        }
        while (true) {
            bucket = bucket.next;
            if (bucket == null) {
                return null;
            }
            if (key.equals(bucket.key)) {
                return bucket.value;
            }
        }
    }

    /*
    /**********************************************************
    /* Helper beans
    /**********************************************************
     */

    private final static class Bucket
    {
        public final TypeKey key;
        public final JsonSerializer<Object> value;
        public final Bucket next;
        
        public Bucket(Bucket next, TypeKey key, JsonSerializer<Object> value)
        {
            this.next = next;
            this.key = key;
            this.value = value;
        }
    }
}
