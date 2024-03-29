package com.lru.memory.disk.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public abstract class AbstractCacheService<T> implements DirLocate {
    
    private static final String KeyInternalGetFromDisk = "f";
    private static final String KeyInternalGetCachedObj = "o";
    private static final int NumberDiskShards = 1000;
    private static final int ConcurrencyLevel = 100;

    protected LRUCache<String, T> cache;
    private final ReentrantReadWriteLock lock;

    private final AtomicLong statsMisses;
    private final AtomicLong statsHits;
    private final AtomicLong statsHitsDisk;
    private final AtomicLong statsHitsMemory;
    private final String cacheName;
    private final int cacheSize;
    private final Map<String, Object> statsMap;
    private boolean persist;
    private String dataDir;
    private CacheLockManager lockMgr;

    public AbstractCacheService(String cacheName, int cacheSize) throws Exception {
        this(cacheName, cacheSize, false, null);
    }
    
    public AbstractCacheService(String cacheName, int cacheSize, boolean diskPersist, String dataDirectory) throws Exception{
        if(Utl.areBlank(cacheName) || cacheSize < 1) throw new Exception("cacheName and/or cacheSize invalid");
        if(diskPersist && Utl.areBlank(dataDirectory) ) throw new Exception("Invalid data directory");
        this.cacheName = cacheName.trim();        
        this.lock = new ReentrantReadWriteLock();
        this.statsHits = new AtomicLong(0L);
        this.statsHitsDisk = new AtomicLong(0L);
        this.statsHitsMemory = new AtomicLong(0L);
        this.statsMisses = new AtomicLong(0L);
        this.cacheSize = cacheSize;
        this.statsMap = new HashMap<>();
        this.persist = diskPersist;        
        this.cache = new LRUCache<>(cacheSize, this);
        this.lockMgr = new CacheLockManager(ConcurrencyLevel);
        if(this.persist) {
            this.dataDir = dataDirectory.trim().replaceAll("/$", "");
            init();
        }
    }
    
    private void init() throws Exception {
        File dir = new File(this.dataDir);
        if(dir.exists() && dir.isFile()) throw new Exception("data dir: " + this.dataDir + ", is a file, should be a directory");
        if(! dir.exists()){
            dir.mkdirs();
        }
        
        for(int i=0;i < NumberDiskShards;i++){
            File shardDir = new File(dir, Integer.toString(i));
            if(shardDir.exists() && shardDir.isDirectory()) continue;
            if(shardDir.exists() && (! shardDir.isDirectory())){
                shardDir.delete();         
            }
            shardDir.mkdir();
        }
    }

    public abstract boolean isCacheItemValid(T o);
    
    private boolean isCacheItemValidInternal(T o) {
        try{
            return isCacheItemValid(o);
        }catch(Exception e){
            return false;
        }
    }

    public abstract T loadData(String key) throws Exception;

    public T get(String key) throws Exception {
        if(Utl.areBlank(key)) throw new Exception("key is blank");
        Map<String, Object> map = internalGetOnly(key, true);
        boolean fromDisk = (Boolean) map.get(KeyInternalGetFromDisk);
        T o = (T) map.get(KeyInternalGetCachedObj);
        //p("Key: " + key + ", try from disk: " + fromDisk + ", obj from internalGetOnly: " + (null != o));
        if (isCacheItemValidInternal(o)) {            
            //Lazy load disk objects into memory
            if(fromDisk){                
                internalPutOnly(key, o, false); //false because we don't want to re-serialize a deserialized object, this is to lazy load to memory
            }
            return o;
        }
        return putWithLookup(key);        
    }

    public T putWithLookup(String key) throws Exception {
        ReentrantReadWriteLock.WriteLock ldWriteLock = this.lockMgr.getLock(key.hashCode()).writeLock();
        ldWriteLock.lock();
        T o = ((T) internalGetOnly(key, false).get(KeyInternalGetCachedObj));
        try {
            if (! isCacheItemValidInternal(o)) {
                o = loadData(key);
                if (null == o) {
                    throw new Exception("Key: " + key + " - Null values not allowed");
                }
                internalPutOnly(key, o, true);
            }
            return o;
        } catch (Exception e) {
            throw e;
        } finally {
            ldWriteLock.unlock();
        }
    }

    public void putOnly(String key, T o) throws Exception {
        internalPutOnly(key, o, true);
    }
    
    private void internalPutOnly(String key, T o, boolean overridePersist) throws Exception {
        if (null == o) {
            throw new Exception("Key: " + key + " - Null values not allowed");
        }
        lock.writeLock().lock();
        try {
            internalPut(key, o, overridePersist);
        } catch (Exception e) {
            throw e;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public T getOnly(String key) throws Exception {
        return ((T) internalGetOnly(key, true).get(KeyInternalGetCachedObj));
    }
    
    private Map<String, Object> internalGetOnly(String key, boolean doStats) throws Exception {
        lock.readLock().lock();
        try {
            Map<String, Object> map = internalGet(key);
            if(doStats){
                boolean tryDisk = (Boolean) map.get(KeyInternalGetFromDisk);
                T t = (T) map.get(KeyInternalGetCachedObj);
                if(null != t){
                    statsHits.incrementAndGet();
                    if(tryDisk) statsHitsDisk.incrementAndGet();
                    else statsHitsMemory.incrementAndGet();
                }else{
                    statsMisses.incrementAndGet();
                }
            }
            return map;
        } catch (Exception e) {
            throw e;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    private Map<String, Object> internalGet(String key) throws Exception{  
        Map<String, Object> map = new HashMap<>();
        try{
            T t = cache.get(key);
            if(persist && (null == t)){
                //long start = System.currentTimeMillis();
                t = deserialize(key);
                //long end = System.currentTimeMillis() - start;
                //if(end > 200) p(key + ", deserialize time: " + end);
                map.put(KeyInternalGetFromDisk, true);                
            }else{
                map.put(KeyInternalGetFromDisk, false);
            }
            map.put(KeyInternalGetCachedObj, t);            
            return map;
        }catch(Exception e){
            throw e;
        }
    }
    
    private void internalPut(String key, T t, boolean overridePersist) throws Exception{        
        try{
            cache.put(key, t);
            if(persist && overridePersist){
                //long start = System.currentTimeMillis();
                serialize(key, t);
                //long end = System.currentTimeMillis() - start;
                //if(end > 200) p(key + ", serialize time: " + end);
            }
        }catch(Exception e){
            throw e;
        }
    }
    
    private void serialize(String key, T t) throws LruCacheSerializationException{
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try{
            fos = new FileOutputStream(this.getPathToFile(key), false);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(t);
        }catch(Exception e){
            throw new LruCacheSerializationException("Unable to serialize key: " + key, e);
        }finally{
            try{fos.close();}catch(Exception e){}
            try{oos.close();}catch(Exception e){}
        }
    }
    
    private T deserialize(String key) throws Exception{
        File f = new File(this.getPathToFile(key));
        if(! f.exists()) return null;
        FileInputStream fis = null;
        ObjectInputStream ois = null;
        try{
            fis = new FileInputStream(f);
            ois = new ObjectInputStream(fis);
            return ( (T) ois.readObject() );
        }catch(Exception e){
            //Don't throw any errors here, delete the existing file
            //This may have been due to deserialization incompatibilities from serial version uid or cached item code changes, etc.
            try{
                f.delete();
            }catch(Exception exd){}
        }finally{
            try{fis.close();}catch(Exception e){}
            try{ois.close();}catch(Exception e){}
        }
        return null;
    }

    public final Map<String, Object> getStats() {
        long hits = statsHits.get();
        long misses = statsMisses.get();
        statsMap.put("cacheName", cacheName);
        statsMap.put("hits", hits);
        statsMap.put("hitsDisk", statsHitsDisk.get());
        statsMap.put("hitsMemory", statsHitsMemory.get());
        statsMap.put("misses", misses);
        statsMap.put("hitratio",
                (hits < 1) ? 0.0 : (((double) hits) / ((double) (hits + misses)))
        );
        statsMap.put("cacheMaxSize", cacheSize);
        statsMap.put("cacheCurrentSize", cache.size());
        return statsMap;
    }
    
    public void clear() throws Exception {        
        lock.writeLock().lock();
        try {
            cache.clear();
        } catch (Exception e) {
            throw e;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /*static void p(Object o){
        System.out.println(o);
    }*/
    
    //Start DirLocate impl
    @Override
    public String getPathToFile(String key) throws Exception {
        if(! isDiskPersistent()) return null;
        if(Utl.areBlank(key)) throw new Exception("key: " + key + " : invalid");
        return this.dataDir + "/" + (Math.abs(key.hashCode()) % NumberDiskShards);
     }
    
    
    //End DirLocate impl

    @Override
    public boolean isDiskPersistent() {
        return this.persist;
    }
    
} //end class
