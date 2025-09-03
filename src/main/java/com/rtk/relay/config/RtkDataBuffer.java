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
    private final ArrayDeque<byte[]> buffer = new ArrayDeque<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final int maxSize;

    public RtkDataBuffer() {
        this.maxSize = 100; // 默认缓存100条数据
    }

    public void addData(byte[] data) {
        if (data == null || data.length == 0) {
            log.warn("尝试添加空数据到缓冲区，已忽略");
            return;
        }

        lock.writeLock().lock();
        try {
            // 如果缓冲区满了��移除最旧的数据
            if (buffer.size() >= maxSize) {
                buffer.removeFirst();
            }
            buffer.addLast(data);
            log.debug("数据已添加到缓冲区，当前缓冲区大小: {}", buffer.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    // 新移动站连接时，发送缓存的最近数据
    public List<byte[]> getRecentData() {
        lock.readLock().lock();
        try {
            List<byte[]> result = new ArrayList<>(buffer);
            log.debug("获取缓冲区数据，返回 {} 条记录", result.size());
            return result;
        } finally {
            lock.readLock().unlock();
        }
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
     * 清空缓冲区
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            buffer.clear();
            log.info("缓冲区已清空");
        } finally {
            lock.writeLock().unlock();
        }
    }
}