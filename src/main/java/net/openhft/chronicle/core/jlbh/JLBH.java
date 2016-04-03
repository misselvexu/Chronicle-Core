/*
 * Copyright 2016 higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.core.jlbh;

import net.openhft.affinity.Affinity;
import net.openhft.affinity.AffinityLock;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.util.Histogram;
import net.openhft.chronicle.core.util.NanoSampler;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Java Latency Benchmark Harness
 * The harness is intended to be used for benchmarks where co-ordinated ommission is an issue.
 * Typically these would be of the producer/consumer nature where the start time for the benchmark
 * may be on a different thread than the end time.
 * <p></p>
 * This tool was inspired by JMH.
 */
public class JLBH implements NanoSampler {
    private static final Double[] NO_DOUBLES = {};
    private final SortedMap<String, Histogram> additionHistograms = new ConcurrentSkipListMap<>();
    private final int rate;
    private final JLBHOptions jlbhOptions;
    private Histogram endToEndHistogram = new Histogram();
    private Histogram osJitterHistogram = new Histogram();
    private long noResultsReturned;
    private AtomicBoolean warmUpComplete = new AtomicBoolean(false);
    //Use non-atomic when so thread synchronisation is necessary
    private boolean warmedUp;

    /**
     * @param jlbhOptions
     */
    public JLBH(JLBHOptions jlbhOptions) {
        this.jlbhOptions = jlbhOptions;
        if (jlbhOptions.jlbhTask == null) throw new IllegalStateException("jlbhTask must be set");
        rate = 1_000_000_000 / jlbhOptions.throughput;
    }

    public NanoSampler addProbe(String name) {
        return additionHistograms.computeIfAbsent(name, n -> new Histogram());
    }

    public void start() {
        jlbhOptions.jlbhTask.init(this);
        OSJitterMonitor osJitterMonitor = new OSJitterMonitor();
        List<double[]> percentileRuns = new ArrayList<>();
        Map<String, List<double[]>> additionalPercentileRuns = new TreeMap<>();

        if (jlbhOptions.recordOSJitter) {
            osJitterMonitor.setDaemon(true);
            osJitterMonitor.start();
        }

        long warmupStart = System.currentTimeMillis();
        for (int i = 0; i < jlbhOptions.warmUpIterations; i++) {
            jlbhOptions.jlbhTask.run(System.nanoTime());
        }

        AffinityLock lock = Affinity.acquireLock();
        try {
            for (int run = 0; run < jlbhOptions.runs; run++) {
                long runStart = System.currentTimeMillis();
                long startTimeNs = System.nanoTime();
                for (int i = 0; i < jlbhOptions.iterations; i++) {

                    if (i == 0 && run == 0) {
                        while (!warmUpComplete.get()) {
                            Jvm.pause(1000);
                            System.out.println("Complete: " + noResultsReturned);
                        }
                        System.out.println("Warm up complete (" + jlbhOptions.warmUpIterations + " iterations took " +
                                ((System.currentTimeMillis()-warmupStart)/1000.0) + "s)");
                        if(jlbhOptions.pauseAfterWarmupMS!=0){
                            System.out.println("Pausing after warmup for " + jlbhOptions.pauseAfterWarmupMS + "ms");
                            Jvm.pause(jlbhOptions.pauseAfterWarmupMS);
                        }
                        runStart = System.currentTimeMillis();
                        startTimeNs = System.nanoTime();
                    } else if (jlbhOptions.accountForCoordinatedOmission) {
                        startTimeNs += rate;
                        while (System.nanoTime() < startTimeNs)
                            ;
                    } else {
                        Jvm.busyWaitMicros(rate / 1000);
                        startTimeNs = System.nanoTime();
                    }

                    jlbhOptions.jlbhTask.run(startTimeNs);
                }

                while (endToEndHistogram.totalCount() < jlbhOptions.iterations) {
                    Thread.yield();
                }
                long totalRunTime = System.currentTimeMillis()-runStart;

                percentileRuns.add(endToEndHistogram.getPercentiles());

                System.out.println("-------------------------------- BENCHMARK RESULTS (RUN " + (run + 1) + ") --------------------------------------------------------");
                System.out.println("Run time: " + totalRunTime/1000.0 + "s");
                System.out.println("Correcting for co-ordinated:" + jlbhOptions.accountForCoordinatedOmission);
                System.out.println("Target throughput:" + jlbhOptions.throughput + "/s" + " = 1 message every " + (rate / 1000) + "us");
                System.out.printf("%-48s", String.format("End to End: (%,d)", endToEndHistogram.totalCount()));
                System.out.println(endToEndHistogram.toMicrosFormat());

                if (additionHistograms.size() > 0) {
                    additionHistograms.entrySet().stream().forEach(e -> {
                        List<double[]> ds = additionalPercentileRuns.computeIfAbsent(e.getKey(),
                                i -> new ArrayList<>());
                        ds.add(e.getValue().getPercentiles());
                        System.out.printf("%-48s", String.format("%s (%,d) ", e.getKey(), e.getValue().totalCount()));
                        System.out.println(e.getValue().toMicrosFormat());
                    });
                }
                if (jlbhOptions.recordOSJitter) {
                    System.out.printf("%-48s", String.format("OS Jitter (%,d)", osJitterHistogram.totalCount()));
                    System.out.println(osJitterHistogram.toMicrosFormat());
                }
                System.out.println("-------------------------------------------------------------------------------------------------------------------");

                noResultsReturned = 0;
                endToEndHistogram.reset();
                additionHistograms.values().stream().forEach(Histogram::reset);
                osJitterMonitor.reset();
            }
        } finally {
            Jvm.pause(5);
            lock.release();
            Jvm.pause(5);
        }

        printPercentilesSummary("end to end", percentileRuns);
        if (additionalPercentileRuns.size() > 0) {
            additionalPercentileRuns.entrySet().stream().forEach(e -> printPercentilesSummary(e.getKey(), e.getValue()));
        }
        jlbhOptions.jlbhTask.complete();
    }

    private void printPercentilesSummary(String label, List<double[]> percentileRuns) {
        System.out.println("-------------------------------- SUMMARY (" + label + ")------------------------------------------------------------");
        List<Double> consistencies = new ArrayList<>();
        double maxValue = Double.MIN_VALUE;
        double minValue = Double.MAX_VALUE;
        int length = percentileRuns.get(0).length;
        for (int i = 0; i < length; i++) {
            double total_log = 0;
            boolean skipFirst = length > 3;
            if(jlbhOptions.skipFirstRun== JLBHOptions.SKIP_FIRST_RUN.SKIP) {
                skipFirst = true;
            }else if(jlbhOptions.skipFirstRun== JLBHOptions.SKIP_FIRST_RUN.NO_SKIP){
                skipFirst = false;
            }
            for (double[] percentileRun : percentileRuns) {
                if (skipFirst) {
                    skipFirst = false;
                    continue;
                }
                double v = percentileRun[i];
                if (v > maxValue)
                    maxValue = v;
                if (v < minValue)
                    minValue = v;
                total_log += Math.log(v);
            }
            consistencies.add(100 * (maxValue - minValue) / (maxValue + minValue / 2));


            double avg_log = total_log / percentileRuns.size();
            double total_sqr_log = 0;
            for (double[] percentileRun : percentileRuns) {
                double v = percentileRun[i];
                double logv = Math.log10(v);
                total_sqr_log += (logv - avg_log) * (logv - avg_log);
            }
            double var_log = total_sqr_log / (percentileRuns.size() - 1);
            consistencies.add(var_log);
            maxValue = Double.MIN_VALUE;
            minValue = Double.MAX_VALUE;
        }

        List<Double> summary = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            for (double[] percentileRun : percentileRuns) {
                summary.add(percentileRun[i] / 1e3);
            }
            summary.add(consistencies.get(i * 2));
            summary.add(consistencies.get(i * 2 + 1));
        }

        StringBuilder sb = new StringBuilder();
        addHeaderToPrint(sb, jlbhOptions.runs);
        System.out.println(sb.toString());

        sb = new StringBuilder();
        addPrToPrint(sb, "50:   ", jlbhOptions.runs);
        addPrToPrint(sb, "90:   ", jlbhOptions.runs);
        addPrToPrint(sb, "99:   ", jlbhOptions.runs);
        addPrToPrint(sb, "99.9: ", jlbhOptions.runs);
        addPrToPrint(sb, "99.99:", jlbhOptions.runs);
        addPrToPrint(sb, "worst:", jlbhOptions.runs);

        System.out.printf(sb.toString(), summary.toArray(NO_DOUBLES));
        System.out.println("-------------------------------------------------------------------------------------------------------------------");
    }

    private void addPrToPrint(StringBuilder sb, String pr, int runs) {
        sb.append(pr);
        for (int i = 0; i < runs; i++) {
            sb.append("%12.2f ");
        }
        sb.append("%12.2f");
        sb.append("%12.2f");
        sb.append("%n");
    }

    private void addHeaderToPrint(StringBuilder sb, int runs) {
        sb.append("Percentile");
        for (int i = 1; i < runs + 1; i++) {
            if (i == 1)
                sb.append("   run").append(i);
            else
                sb.append("         run").append(i);
        }
        sb.append("      % Variation");
        sb.append("   var(log)");
    }

    @Override
    public void sampleNanos(long nanos) {
        sample(nanos);
    }

    public void sample(long nanoTime) {
        noResultsReturned++;
        if (noResultsReturned < jlbhOptions.warmUpIterations && !warmedUp) {
            endToEndHistogram.sample(nanoTime);
            return;
        }
        if (noResultsReturned == jlbhOptions.warmUpIterations && !warmedUp) {
            warmedUp = true;
            endToEndHistogram.reset();
            if (additionHistograms.size() > 0) {
                additionHistograms.values().forEach(Histogram::reset);
            }
            warmUpComplete.set(true);
            return;
        }
        endToEndHistogram.sample(nanoTime);
    }

    private class OSJitterMonitor extends Thread {
        final AtomicBoolean reset = new AtomicBoolean(false);

        public void run() {
            // make sure this thread is not bound by its parent.
            Affinity.setAffinity(AffinityLock.BASE_AFFINITY);

            long lastTime = System.nanoTime();
            while (true) {
                if (reset.get()) {
                    reset.set(false);
                    osJitterHistogram.reset();
                    lastTime = System.nanoTime();
                }
                long time = System.nanoTime();
                if (time - lastTime > jlbhOptions.recordJitterGreaterThanNs) {
                    //System.out.println("DELAY " + (time - lastTime) / 100_000 / 10.0 + "ms");
                    osJitterHistogram.sample(time - lastTime);
                }
                lastTime = time;
            }

        }

        void reset() {
            reset.set(true);
        }
    }
}
