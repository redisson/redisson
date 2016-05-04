package org.redisson;

import java.util.stream.IntStream;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

public class TestNameToConsoleRunListener extends RunListener {

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
