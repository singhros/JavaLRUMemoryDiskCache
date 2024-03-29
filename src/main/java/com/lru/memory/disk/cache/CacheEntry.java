package com.lru.memory.disk.cache;

import java.io.Serializable;

/**
 *
 *  This is basic utility object you can use as a wrapper object for the real
 *  object you want to cache.
 *
 */
public class CacheEntry<T extends Serializable> implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final T t;
    private final long timestamp;
    
    public CacheEntry(T t, long timestamp){
        this.t=t;
        this.timestamp=timestamp;
    }
    
    public T getCached(){
        return this.t;
    }
    
    public long getTimestamp(){
        return this.timestamp;
    }
}
