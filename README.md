# Java LRU Memory Disk Cache

This is a thread safe, easy to use Java LRU in memory and disk cache.  

the LRU, they will also be removed from disk (If using disk caching).  The disk cache will contain as many and possibly more items than are present in memory.  This will be evident when the cache (memory and disk) is used over your application's stop start cycles.

## Install (Maven)

* From the directory you downloaded the jar, run the following command to do a local maven install:  
  `mvn install:install-file -Dfile=JavaLRUMemoryDiskCache-1.1.jar -DgroupId=com.lru.memory.disk.cache -DartifactId=JavaLRUMemoryDiskCache -Dversion=1.1 -Dpackaging=jar`
* Add to your project's `<dependencies>`  
```xml
<dependency>
    <groupId>com.lru.memory.disk.cache</groupId>
    <artifactId>JavaLRUMemoryDiskCache</artifactId>
    <version>1.1</version>
    <exclusions>
        <exclusion> 
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

## To do:
* Distributed caching
