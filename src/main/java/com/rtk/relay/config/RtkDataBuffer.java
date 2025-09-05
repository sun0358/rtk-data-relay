package com.rtk.relay.config;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
@Slf4j
public class RtkDataBuffer {
    // 使用ArrayDeque作为环形缓冲区存储最近的数据
    private final ArrayDeque<RtkDataEntry> buffer = new ArrayDeque<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final int maxSize;
    private volatile long totalMemoryUsage = 0; // 内存使用统计
    private static final long MAX_MEMORY_USAGE = 10 * 1024 * 1024; // 最大10MB内存使用

    public RtkDataBuffer() {
        this.maxSize = 200; // 增加缓存容量到200条数据
    }
    
    /**
     * RTK数据条目，包含数据和时间戳
     */
    public static class RtkDataEntry {
        private final byte[] data;
        private final long timestamp;
        
        public RtkDataEntry(byte[] data) {
            this.data = data.clone(); // 防止外部修改
            this.timestamp = System.currentTimeMillis();
        }
        
        public byte[] getData() {
            return data.clone(); // 返回副本，防止外部修改
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public int getSize() {
            return data.length;
        }
        
        public boolean isExpired(long maxAgeMs) {
            return System.currentTimeMillis() - timestamp > maxAgeMs;
        }
    }

    /**
     * 添加数据到缓冲区
     * 支持内存限制和数据过期清理
     */
    public void addData(byte[] data) {
        if (data == null || data.length == 0) {
            log.warn("尝试添加空数据到缓冲区，已忽略");
            return;
        }
        
        // 检查数据大小是否合理（单条数据不超过1MB）
        if (data.length > 1024 * 1024) {
            log.warn("数据过大，忽略添加到缓冲区 - 大小: {} 字节", data.length);
            return;
        }

        lock.writeLock().lock();
        try {
            RtkDataEntry entry = new RtkDataEntry(data);
            
            // 清理过期数据（超过5分钟的数据）
            cleanupExpiredData(5 * 60 * 1000L);
            
            // 内存限制检查：如果添加新数据会超过内存限制，清理旧数据
            while (totalMemoryUsage + data.length > MAX_MEMORY_USAGE && !buffer.isEmpty()) {
                RtkDataEntry removed = buffer.removeFirst();
                totalMemoryUsage -= removed.getSize();
                log.debug("内存限制清理旧数据 - 大小: {} 字节", removed.getSize());
            }
            
            // 数量限制检查：如果缓冲区满了，移除最旧的数据
            while (buffer.size() >= maxSize && !buffer.isEmpty()) {
                RtkDataEntry removed = buffer.removeFirst();
                totalMemoryUsage -= removed.getSize();
                log.debug("数量限制清理旧数据 - 大小: {} 字节", removed.getSize());
            }
            
            // 添加新数据
            buffer.addLast(entry);
            totalMemoryUsage += data.length;
            
            if (log.isDebugEnabled()) {
                log.debug("数据已添加到缓冲区 - 大小: {} 字节, 缓冲区: {}/{} 条, 内存: {}/{} KB", 
                    data.length, buffer.size(), maxSize, 
                    totalMemoryUsage / 1024, MAX_MEMORY_USAGE / 1024);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 清理过期数据
     */
    private void cleanupExpiredData(long maxAgeMs) {
        int cleanedCount = 0;
        while (!buffer.isEmpty() && buffer.peekFirst().isExpired(maxAgeMs)) {
            RtkDataEntry removed = buffer.removeFirst();
            totalMemoryUsage -= removed.getSize();
            cleanedCount++;
        }
        if (cleanedCount > 0) {
            log.debug("清理过期数据 {} 条", cleanedCount);
        }
    }

    /**
     * 新移动站连接时，获取最近的数据
     * @param maxCount 最大返回数据条数，0表示返回所有
     * @param maxAgeMs 最大数据年龄（毫秒），0表示不限制
     * @return 最近的数据列表
     */
    public List<byte[]> getRecentData(int maxCount, long maxAgeMs) {
        lock.readLock().lock();
        try {
            List<byte[]> result = new ArrayList<>();
            int count = 0;
            
            // 从最新的数据开始遍历
            for (RtkDataEntry entry : buffer) {
                // 检查数据年龄
                if (maxAgeMs > 0 && entry.isExpired(maxAgeMs)) {
                    continue; // 跳过过期数据
                }
                
                result.add(entry.getData());
                count++;
                
                // 检查数量限制
                if (maxCount > 0 && count >= maxCount) {
                    break;
                }
            }
            
            log.debug("获取缓冲区数据，返回 {} 条记录（总共 {} 条可用）", result.size(), buffer.size());
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取最近的数据（默认参数）
     */
    public List<byte[]> getRecentData() {
        return getRecentData(50, 2 * 60 * 1000L); // 最多50条，最近2分钟的数据
    }

    /**
     * 获取当前缓冲区大小
     */
    public int getBufferSize() {
        lock.readLock().lock();
        try {
            return buffer.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取缓冲区内存使用情况
     */
    public long getMemoryUsage() {
        return totalMemoryUsage;
    }
    
    /**
     * 获取缓冲区统计信息
     */
    public String getBufferStats() {
        lock.readLock().lock();
        try {
            return String.format("Buffer[Size: %d/%d, Memory: %d/%d KB, Oldest: %s]",
                    buffer.size(), maxSize,
                    totalMemoryUsage / 1024, MAX_MEMORY_USAGE / 1024,
                    buffer.isEmpty() ? "N/A" : formatAge(buffer.peekFirst().getTimestamp()));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    private String formatAge(long timestamp) {
        long ageMs = System.currentTimeMillis() - timestamp;
        if (ageMs < 1000) return ageMs + "ms";
        if (ageMs < 60000) return (ageMs / 1000) + "s";
        return (ageMs / 60000) + "m";
    }

    /**
     * 清空缓冲区
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            int oldSize = buffer.size();
            long oldMemory = totalMemoryUsage;
            
            buffer.clear();
            totalMemoryUsage = 0;
            
            log.info("缓冲区已清空 - 清理 {} 条数据，释放 {} KB 内存", 
                    oldSize, oldMemory / 1024);
        } finally {
            lock.writeLock().unlock();
        }
    }
}