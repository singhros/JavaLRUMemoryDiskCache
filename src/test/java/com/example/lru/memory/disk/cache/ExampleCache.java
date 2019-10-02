package com.example.lru.memory.disk.cache;

import com.lru.memory.disk.cache.AbstractCacheService;


public class ExampleCache extends AbstractCacheService<ExampleObjectToCache>{   
    private final ExampleDao exampleDao;
    
    //Example of constructor that creates an in memory cache only
    public ExampleCache(String cacheName, int cacheSize, ExampleDao exampleDao) throws Exception{
        super(cacheName, cacheSize);
        this.exampleDao = exampleDao;
    }
    
    //Example of constructor that creates an in memory and disk cache
    public ExampleCache(String cacheName, int cacheSize, boolean diskPersist, String dataDirectory, ExampleDao exampleDao) throws Exception {
        super(cacheName, cacheSize, diskPersist, dataDirectory);
        this.exampleDao = exampleDao;
    }

    
    @Override
    public boolean isCacheItemValid(ExampleObjectToCache o) {
        //return o.isValid();        
        return (null != o);
    }

    /*
        You decide how to load the object you want to cache.
        This could be an api call, database call, etc.
    */
    @Override
    public ExampleObjectToCache loadData(String key) throws Exception {
        return this.exampleDao.get(key);
    }    
}
