package org.redisson;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

public class RedissonTestRunListener extends RunListener {

    private final AtomicBoolean running = new AtomicBoolean(Boolean.TRUE);

    @Override
    public void testRunStarted(Description description) throws Exception {
        super.testRunStarted(description);
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
                    Logger.getLogger(RedissonTestRunListener.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }).start();
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
        super.testRunFinished(result);
        running.set(Boolean.FALSE);
    }

    @Override
    public void testStarted(Description d) throws Exception {
        super.testStarted(d);
        printTestName("Started", d.getDisplayName(), '*');
    }

    @Override
    public void testFinished(Description d) throws Exception {
        super.testFinished(d);
        printTestName("Finished", d.getDisplayName());
    }

    @Override
    public void testIgnored(Description d) throws Exception {
        super.testIgnored(d);
        printTestName("Ignored", d.getDisplayName());
    }

    @Override
    public void testFailure(Failure f) throws Exception {
        super.testFailure(f);
        printTestName("Failed", f.getTestHeader());
    }

    @Override
    public void testAssumptionFailure(Failure f) {
        super.testAssumptionFailure(f);
        printTestName("Assumption Failed", f.getTestHeader());
    }

    private static void printTestName(String action, String test) {
        printTestName(action, test, '=');
    }

    private static void printTestName(String action, String test, char c) {
        int dividers = 16 + action.length() + test.length();
        aBeautifulDivider(dividers, c);
        System.out.println("    " + action + " Test:  " + test);
        aBeautifulDivider(dividers, c);
    }

    private static void aBeautifulDivider(int times, char c) {
        System.out.println("");
        IntStream.iterate(0, n -> n++)
                .limit(times)
                .forEach((i) -> System.out.print(c));
        System.out.println("\n");
    }
}
