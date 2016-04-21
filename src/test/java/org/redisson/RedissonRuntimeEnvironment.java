package org.redisson;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RedissonRuntimeEnvironment {

    public static final boolean isTravis = "true".equalsIgnoreCase(System.getProperty("travisEnv"));
    public static final String redisBinaryPath = System.getProperty("redisBinary", "C:\\Devel\\projects\\redis\\Redis-x64-3.0.500\\redis-server.exe");
    public static final String tempDir = System.getProperty("java.io.tmpdir");
    public static final String OS;
    public static final boolean isWindows;

    static {
        OS = System.getProperty("os.name", "generic");
        isWindows = OS.toLowerCase(Locale.ENGLISH).contains("win");
        if (RedissonRuntimeEnvironment.isTravis) {
            final AtomicBoolean running = new AtomicBoolean(Boolean.TRUE);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(Boolean.FALSE);
            }));
            new Thread(() -> {
                final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
                final AtomicLong u = new AtomicLong(runtimeBean.getUptime());
                while (running.get()) {
                    try {
                        long upTime = runtimeBean.getUptime();
                        if (upTime >= u.get() + 10000) {
                            u.set(upTime);
                            System.out.printf("Test Up Time    = %.3f (s)%n", upTime / 1000d);
                            System.out.printf("Heap Usage      = %.3f (MB)%n", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / 1024d / 1024d);
                            System.out.printf("None Heap Usage = %.3f (MB)%n", ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed() / 1024d / 1024d);
                            System.out.println("=============================");
                        }
                        Thread.currentThread().sleep(10000l);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(RedissonRuntimeEnvironment.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }).start();
        }
    }
}
