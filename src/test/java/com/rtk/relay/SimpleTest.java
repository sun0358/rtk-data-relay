package com.rtk.relay;

import java.io.*;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * RTK服务简单测试程序
 * 用于快速验证数据转发功能
 */
public class SimpleTest {

    /**
     * 生成指定长度的随机16进制数据
     */
    private static byte[] generateRandomHexData(int length) {
        Random random = new Random();
        byte[] data = new byte[length];
        random.nextBytes(data);
        return data;
    }

    /**
     * 将字节数组转换为16进制字符串（用于显示）
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== RTK数据转发测试开始 ===");

        // 启动移动站接收线程
        Thread mobileStation = new Thread(() -> {
            try (Socket socket = new Socket("192.168.5.114", 9002)) {
                System.out.println("✅ 移动站已连接到端口9002");
                InputStream in = socket.getInputStream();
                byte[] buffer = new byte[1024];
                int dataCount = 0;

                while (true) {
                    int len = in.read(buffer);
                    if (len > 0) {
                        dataCount++;
                        byte[] receivedData = new byte[len];
                        System.arraycopy(buffer, 0, receivedData, 0, len);

                        System.out.println("📥 移动站收到数据包 #" + dataCount +
                                " (" + len + " 字节): " +
                                bytesToHex(receivedData));
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ 移动站错误: " + e.getMessage());
            }
        });
        mobileStation.setDaemon(false); // 设为非守护线程，保持程序运行
        mobileStation.start();

        // 等待移动站���接稳定
        System.out.println("⏳ 等待移动站连接稳定...");
        TimeUnit.SECONDS.sleep(2);

        // 启动基站发送线程
        Thread baseStation = new Thread(() -> {
            try (Socket socket = new Socket("192.168.5.114", 9003)) {
                System.out.println("✅ 基站已连接到端口9003");
                OutputStream out = socket.getOutputStream();
                int packetCount = 0;

                while (true) {
                    packetCount++;

                    // 生成100字节的随机16进制数据
                    byte[] data = generateRandomHexData(100);

                    // 发送数据
                    out.write(data);
                    out.flush();

                    System.out.println("📤 基站发送数据包 #" + packetCount +
                            " (100 字节): " +
                            bytesToHex(data).substring(0, Math.min(50, bytesToHex(data).length())) + "...");

                    // 每隔1秒发送一次
                    TimeUnit.SECONDS.sleep(1);
                }
            } catch (Exception e) {
                System.err.println("❌ 基站错误: " + e.getMessage());
            }
        });
        baseStation.setDaemon(false); // 设为非守护线程
        baseStation.start();

        // 主线程等待，保持程序运行
        System.out.println("🚀 测试程序启动完成，基站将每秒发送100字节数据");
        System.out.println("💡 按 Ctrl+C 停止测试");

        // 等待子线程结束（实际上会一直运行）
        try {
            mobileStation.join();
            baseStation.join();
        } catch (InterruptedException e) {
            System.out.println("⛔ 测试程序被中断");
        }

        System.out.println("=== RTK数据转发测试结束 ===");
    }
}