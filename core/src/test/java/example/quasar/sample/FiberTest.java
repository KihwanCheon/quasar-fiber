package example.quasar.sample;

import co.paralleluniverse.common.util.Exceptions;
import co.paralleluniverse.fibers.*;
import co.paralleluniverse.io.serialization.ByteArraySerializer;
import co.paralleluniverse.strands.*;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class FiberTest {

    private transient FiberScheduler scheduler;

    @SuppressWarnings("unused")
    private static Strand.UncaughtExceptionHandler previousUEH;

    @BeforeEach
    void setUp() {
        // scheduler = new FiberForkJoinScheduler("test", 4, null, false);
        scheduler = fiberSchedulerAllWaysNewThreadByFixedThreadPool();
    }

    @SuppressWarnings("unused")
    private static FiberExecutorScheduler fiberSchedulerAllWaysNewThreadByFixedThreadPool() {
        return new FiberExecutorScheduler("test",
                Executors.newFixedThreadPool(1,
                        new ThreadFactoryBuilder().setPriority(3).setNameFormat("fiber-scheduler-%d").setDaemon(true).build()));
    }

    @SuppressWarnings("unused")
    private static FiberExecutorScheduler fiberSchedulerAllWaysNewThreadCachedThreadPool() {
        return new FiberExecutorScheduler("test",
                Executors.newCachedThreadPool());
    }

    @BeforeAll
    static void beforeAll() {
        previousUEH = Fiber.getDefaultUncaughtExceptionHandler();
        Fiber.setDefaultUncaughtExceptionHandler((s, e) -> Exceptions.rethrow(e));
    }

    @AfterAll
    static void afterAll() {
        Fiber.setDefaultUncaughtExceptionHandler(previousUEH);
    }

    @Test
    void test_priority() {
        @SuppressWarnings("rawtypes")
        var fiber = new Fiber(scheduler, (SuspendableRunnable) () -> log.info("test_priority fiber run!"));

        assertThat(fiber.getPriority()).isEqualTo(Strand.NORM_PRIORITY);

        fiber.setPriority(3);
        log.info("fiber.setPriority(3)");
        assertThat(fiber.getPriority()).isEqualTo(3);

        try {
            log.info("fiber.setPriority(Strand.MAX_PRIORITY + 1)");
            fiber.setPriority(Strand.MAX_PRIORITY + 1);
            fail();
        } catch (IllegalArgumentException ignore) {
            log.info("fiber.setPriority(Strand.MAX_PRIORITY + 1) throw ex");
        }

        try {
            log.info("fiber.setPriority(Strand.MIN_PRIORITY - 1)");
            fiber.setPriority(Strand.MIN_PRIORITY - 1);
            fail();
        } catch (IllegalArgumentException ignore) {
            log.info("fiber.setPriority(Strand.MIN_PRIORITY - 1) throw ex");
        }

        log.info("exit");
    }

    @Test
    public void testTimeout() throws Exception {
        @SuppressWarnings("rawtypes")
        var fiber = new Fiber(scheduler,
                () -> {
                    log.info("before park 100 milli");
                    Fiber.park(100, TimeUnit.MILLISECONDS);
                    log.info("after park 100 milli");
                }).start();

        try {
            log.info("before join 50 milli");
            fiber.join(50, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException ignored) {
            log.info("join 50 milli throw ex");
        }

        log.info("before join 200 milli");
        fiber.join(200, TimeUnit.MILLISECONDS);
        log.info("after join 200 milli");
    }

    @Test
    public void testJoinFromFiber() throws Exception {
        final var fiber1 = new Fiber<>(scheduler, (SuspendableCallable<Integer>) () -> {
            log.info("fiber1 before park");
            Fiber.park(100, TimeUnit.MILLISECONDS);
            log.info("fiber1 return 123");
            return 123;
        }).start();

        final var fiber2 = new Fiber<>(scheduler, (SuspendableCallable<Integer>) () -> {
            try {
                log.info("fiber2 before return fiber1.get()");
                return (int) fiber1.get();
            } catch (ExecutionException e) {
                throw Exceptions.rethrow(e.getCause());
            }
        }).start();

        log.info("before fiber2.get()");
        int res = fiber2.get();
        log.info("end fiber2.get()");

        assertThat(res).isEqualTo(123);
        assertThat(fiber1.get()).isEqualTo(123);
    }

    @Test
    public void testInterrupt() throws Exception {
        @SuppressWarnings("rawtypes")
        var fiber = new Fiber(scheduler, () -> {
            try {
                log.info("before fiber sleep(100)");
                Fiber.sleep(100);
                fail("InterruptedException not thrown");
            } catch (InterruptedException e) {
                log.info("InterruptedException throw ex");
            }
        }).start();

        log.info("before thread sleep 20");
        Thread.sleep(20);
        log.info("after thread sleep 20, before fiber interrupt");
        fiber.interrupt();

        log.info("before fiber join");
        fiber.join(5, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testCancel1() throws Exception {
        final AtomicBoolean started = new AtomicBoolean();
        final AtomicBoolean terminated = new AtomicBoolean();

        @SuppressWarnings("rawtypes")
        var fiber = new Fiber(scheduler, () -> {
            started.set(true);
            try {
                Fiber.sleep(100);
                fail("InterruptedException not thrown");
            } catch (InterruptedException e) {
                log.info("InterruptedException throw ex");
            }
            terminated.set(true);
        });

        fiber.start();
        Thread.sleep(20);
        fiber.cancel(true);
        fiber.join(5, TimeUnit.MILLISECONDS);
        assertThat(started.get()).isEqualTo(true);
        assertThat(terminated.get()).isEqualTo(true);
    }

    @Test
    public void testCancel2() throws Exception {
        final AtomicBoolean started = new AtomicBoolean();
        final AtomicBoolean terminated = new AtomicBoolean();
        @SuppressWarnings("rawtypes")
        var fiber = new Fiber(scheduler, () -> {
            started.set(true);
            try {
                Fiber.sleep(100);
                fail("InterruptedException not thrown");
            } catch (InterruptedException ignore) {
            }
            terminated.set(true);
        });

        fiber.cancel(true);
        fiber.start();
        Thread.sleep(20);
        try {
            fiber.join(5, TimeUnit.MILLISECONDS);
            fail();
        } catch (CancellationException ignore) {
        }
        assertThat(started.get()).isEqualTo(false);
        assertThat(terminated.get()).isEqualTo(false);
    }

    @Test
    public void testThreadLocals() throws Exception {
        final ThreadLocal<String> tl1 = new ThreadLocal<>();
        final InheritableThreadLocal<String> tl2 = new InheritableThreadLocal<>();
        tl1.set("foo");
        tl2.set("bar");

        @SuppressWarnings("rawtypes")
        var fiber = new Fiber(scheduler, () -> {
            assertThat(tl1.get()).isNull();
            assertThat(tl2.get()).isEqualTo("bar");

            tl1.set("koko");
            tl2.set("bubu");

            assertThat(tl1.get()).isEqualTo("koko");
            assertThat(tl2.get()).isEqualTo("bubu");

            Fiber.sleep(100);

            assertThat(tl1.get()).isEqualTo("koko");
            assertThat(tl2.get()).isEqualTo("bubu");
        });
        fiber.start();
        fiber.join();

        assertThat(tl1.get()).isEqualTo("foo");
        assertThat(tl2.get()).isEqualTo("bar");
    }

    @Test
    public void testNoLocals() { // shitty test
        final ThreadLocal<String> tl1 = new ThreadLocal<>();
        final InheritableThreadLocal<String> tl2 = new InheritableThreadLocal<>();
        tl1.set("foo");
        tl2.set("bar");

        var tMain = Thread.currentThread();
        log.info("main thread set foo, bar / {}-{} :{}, {}", tMain, tMain.hashCode(), tl1.get(), tl2.get());

        @SuppressWarnings("rawtypes")
        var fiber = new Fiber(scheduler,
                () -> {
                    var tFiber1 = Thread.currentThread();
                    log.info("fiber thread find foo, bar / {}-{} :{}, {}", tFiber1, tFiber1.hashCode(), tl1.get(), tl2.get());
                    assertThat(tl1.get()).isNull();
                    assertThat(tl2.get()).isNotNull();

                    log.info("fiber thread set koko bubu");
                    tl1.set("koko");
                    tl2.set("bubu");

                    log.info("fiber thread get koko bubu");
                    assertThat(tl1.get()).isEqualTo("koko");
                    assertThat(tl2.get()).isEqualTo("bubu");
                });//.setNoLocals(true);

        fiber.start();

        try {
            fiber.join();
        } catch (ExecutionException | InterruptedException ignore) {
        }

        log.info("main thread get foo, bar");
        assertThat(tl1.get()).isEqualTo("foo");
        assertThat(tl2.get()).isEqualTo("bar");

        @SuppressWarnings("rawtypes")
        var fiber2 = new Fiber(scheduler,
                new SuspendableRunnable() {
                    @Override
                    public void run() throws SuspendExecution, InterruptedException {
                        var tFiber2 = Thread.currentThread();
                        log.info("fiber2 thread get koko bubu / {}-{} :{}, {}", tFiber2, tFiber2.hashCode(), tl1.get(), tl2.get());
                        assertThat(tl1.get()).isNull();
                        assertThat(tl2.get()).isEqualTo("bar");
                    }
                });
        fiber2.start();

        try {
            fiber2.join();
        } catch (ExecutionException | InterruptedException ignore) {
        }
    }

    @Test
    public void testInheritThreadLocals() throws Exception {
        final ThreadLocal<String> tl1 = new ThreadLocal<>();
        tl1.set("foo");

        @SuppressWarnings("rawtypes")
        var fiber = new Fiber(scheduler, () -> {
            assertThat(tl1.get()).isEqualTo("foo");

            Fiber.sleep(100);

            assertThat(tl1.get()).isEqualTo("foo");

            tl1.set("koko");

            assertThat(tl1.get()).isEqualTo("koko");

            Fiber.sleep(100);

            assertThat(tl1.get()).isEqualTo("koko");
        });
        fiber.inheritThreadLocals().start();
        fiber.join();

        assertThat(tl1.get()).isEqualTo("foo");
    }



    @Test
    public void testThreadLocalsParallel() throws Exception {
        final ThreadLocal<String> tl = new ThreadLocal<>();

        final int n = 100;
        final int loops = 100;
        var fibers = new Fiber[n];
        for (int i = 0; i < n; i++) {
            final int id = i;

            @SuppressWarnings("rawtypes")
            var fiber = new Fiber(scheduler, () -> {
                for (int j = 0; j < loops; j++) {
                    final String tlValue = "tl-" + id + "-" + j;
                    tl.set(tlValue);
                    assertThat(tl.get()).isEqualTo(tlValue);
                    Strand.sleep(10);
                    assertThat(tl.get()).isEqualTo(tlValue);
                }
            });
            fiber.start();
            fibers[i] = fiber;
        }

        for (var fiber : fibers)
            fiber.join();
    }

    @Test
    public void testInheritThreadLocalsParallel() throws Exception {
        final ThreadLocal<String> tl = new ThreadLocal<>();
        tl.set("foo");

        final int n = 100;
        final int loops = 100;
        var fibers = new Fiber[n];
        for (int i = 0; i < n; i++) {
            final int id = i;
            @SuppressWarnings("rawtypes")
            Fiber fiber = new Fiber(scheduler, () -> {
                for (int j = 0; j < loops; j++) {
                    final String tlValue = "tl-" + id + "-" + j;
                    tl.set(tlValue);
                    assertThat(tl.get()).isEqualTo(tlValue);
                    Strand.sleep(10);
                    assertThat(tl.get()).isEqualTo(tlValue);
                }
            }).inheritThreadLocals();
            fiber.start();
            fibers[i] = fiber;
        }

        for (var fiber : fibers)
            fiber.join();
    }

    @Test
    public void whenFiberIsNewThenDumpStackReturnsNull() {
        @SuppressWarnings("rawtypes")
        var fiber = new Fiber(scheduler, new SuspendableRunnable() {
            @Override
            public void run() throws SuspendExecution, InterruptedException {
                foo();
            }

            private void foo() {
            }
        });

        StackTraceElement[] st = fiber.getStackTrace();
        assertThat(st).isNull();
    }

    @Test
    public void whenFiberIsTerminatedThenDumpStackReturnsNull() throws Exception {
        @SuppressWarnings("rawtypes")
        var fiber = new Fiber(scheduler, new SuspendableRunnable() {
            @Override
            public void run() throws SuspendExecution, InterruptedException {
                foo();
            }

            private void foo() {
            }
        }).start();

        fiber.join();

        StackTraceElement[] st = fiber.getStackTrace();
        assertThat(st).isNull();
    }

    @Test
    public void testDumpStackCurrentFiber() throws Exception {
        @SuppressWarnings("rawtypes")
        var fiber = new Fiber(scheduler, new SuspendableRunnable() {
            @Override
            public void run() throws SuspendExecution, InterruptedException {
                foo();
            }

            private void foo() {
                StackTraceElement[] st = Fiber.currentFiber().getStackTrace();

                // Strand.printStackTrace(st, System.err);
                assertThat(st[0].getMethodName()).isEqualTo("getStackTrace");
                assertThat(st[1].getMethodName()).isEqualTo("foo");
                assertThat(st[st.length - 1].getMethodName()).isEqualTo("run");
                assertThat(st[st.length - 1].getClassName()).isEqualTo(Fiber.class.getName());
            }
        }).start();

        fiber.join();
    }

    @Test
    public void testDumpStackRunningFiber() throws Exception {
        @SuppressWarnings("rawtypes")
        var fiber = new Fiber(scheduler, new SuspendableRunnable() {
            @Override
            public void run() throws SuspendExecution, InterruptedException {
                foo();
            }

            private void foo() {
                final long start = System.nanoTime();
                for (;;) {
                    if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) > 1000)
                        break;
                }
            }
        }).start();

        Thread.sleep(200);

        var st = fiber.getStackTrace();

        // Strand.printStackTrace(st, System.err);
        boolean found = false;
        for (var stackTraceElement : st) {
            if (stackTraceElement.getMethodName().equals("foo")) {
                found = true;
                break;
            }
        }
        assertThat(found).isEqualTo(true);
        assertThat(st[st.length - 1].getMethodName()).isEqualTo("run");
        assertThat(st[st.length - 1].getClassName()).isEqualTo(Fiber.class.getName());

        fiber.join();
    }

    @Test
    public void testDumpStackWaitingFiber() throws Exception {
        final Condition cond = new SimpleConditionSynchronizer(null);
        final AtomicBoolean flag = new AtomicBoolean(false);

        @SuppressWarnings("rawtypes")
        var fiber = new Fiber(scheduler, new SuspendableRunnable() {
            @Override
            public void run() throws SuspendExecution, InterruptedException {
                foo();
            }

            private void foo() throws InterruptedException, SuspendExecution {
                Object token = cond.register();
                try {
                    for (int i = 0; !flag.get(); i++)
                        cond.await(i);
                } finally {
                    cond.unregister(token);
                }
            }
        }).start();

        Thread.sleep(200);

        StackTraceElement[] st = fiber.getStackTrace();

        // Strand.printStackTrace(st, System.err);
        assertThat(st[0].getMethodName()).isEqualTo("park");
        boolean found = false;
        for (StackTraceElement ste : st) {
            if (ste.getMethodName().equals("foo")) {
                found = true;
                break;
            }
        }
        assertThat(found).isEqualTo(true);
        assertThat(st[st.length - 1].getMethodName()).isEqualTo("run");
        assertThat(st[st.length - 1].getClassName()).isEqualTo(Fiber.class.getName());

        flag.set(true);
        cond.signalAll();

        fiber.join();
    }

    @Test
    public void testDumpStackWaitingFiberWhenCalledFromFiber() throws Exception {
        final Condition cond = new SimpleConditionSynchronizer(null);
        final AtomicBoolean flag = new AtomicBoolean(false);

        @SuppressWarnings("rawtypes")
        final var fiber = new Fiber(scheduler, new SuspendableRunnable() {
            @Override
            public void run() throws SuspendExecution, InterruptedException {
                foo();
            }

            private void foo() throws InterruptedException, SuspendExecution {
                Object token = cond.register();
                try {
                    for (int i = 0; !flag.get(); i++)
                        cond.await(i);
                } finally {
                    cond.unregister(token);
                }
            }
        }).start();

        Thread.sleep(200);

        @SuppressWarnings("rawtypes")
        var fiber2 = new Fiber(scheduler, () -> {
            StackTraceElement[] st = fiber.getStackTrace();

            // Strand.printStackTrace(st, System.err);
            assertThat(st[0].getMethodName()).isEqualTo("park");
            boolean found = false;
            for (StackTraceElement ste : st) {
                if (ste.getMethodName().equals("foo")) {
                    found = true;
                    break;
                }
            }
            assertThat(found).isEqualTo(true);
            assertThat(st[st.length - 1].getMethodName()).isEqualTo("run");
            assertThat(st[st.length - 1].getClassName()).isEqualTo(Fiber.class.getName());
        }).start();

        fiber2.join();

        flag.set(true);
        cond.signalAll();

        fiber.join();
    }

    @Test
    public void testDumpStackSleepingFiber() throws Exception {
        // sleep is a special case
        @SuppressWarnings("rawtypes")
        var fiber = new Fiber(scheduler, new SuspendableRunnable() {
            @Override
            public void run() throws SuspendExecution, InterruptedException {
                foo();
            }

            private void foo() throws InterruptedException, SuspendExecution {
                Fiber.sleep(1000);
            }
        }).start();

        Thread.sleep(200);

        StackTraceElement[] st = fiber.getStackTrace();

        // Strand.printStackTrace(st, System.err);
        assertThat(st[0].getMethodName()).isEqualTo("sleep");
        boolean found = false;
        for (var e : st) {
            if (e.getMethodName().equals("foo")) {
                found = true;
                break;
            }
        }
        assertThat(found).isEqualTo(true);
        assertThat(st[st.length - 1].getMethodName()).isEqualTo("run");
        assertThat(st[st.length - 1].getClassName()).isEqualTo(Fiber.class.getName());

        fiber.join();
    }

    @Test
    public void testBadFiberDetection() throws Exception {
        @SuppressWarnings("rawtypes")
        var good = new Fiber("good", scheduler, () -> {
            for (int i = 0; i < 100; i++)
                Strand.sleep(10);
        }).start();

        @SuppressWarnings("rawtypes")
        var bad = new Fiber("bad", scheduler, () -> {
            final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1000);
            for (;;) {
                if (System.nanoTime() >= deadline)
                    break;
            }
        }).start();

        good.join();
        bad.join();
    }

    @Test
    public void testUncaughtExceptionHandler() throws Exception {
        final AtomicReference<Throwable> t = new AtomicReference<>();

        var f = new Fiber<Void>() {
            @Override
            protected Void run() throws SuspendExecution, InterruptedException {
                throw new RuntimeException("foo");
            }
        };

        f.setUncaughtExceptionHandler((f1, e) -> t.set(e));

        f.start();

        try {
            f.join();
            fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause().getMessage()).isEqualTo("foo");
        }

        assertThat(t.get().getMessage()).isEqualTo("foo");
    }

    @Test
    public void testDefaultUncaughtExceptionHandler() throws Exception {
        final AtomicReference<Throwable> t = new AtomicReference<>();

        var f = new Fiber<Void>() {
            @Override
            protected Void run() throws SuspendExecution, InterruptedException {
                throw new RuntimeException("foo");
            }
        };

        Fiber.setDefaultUncaughtExceptionHandler((f1, e) -> t.set(e));

        f.start();

        try {
            f.join();
            fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause().getMessage()).isEqualTo("foo");
        }
        final Throwable th = t.get();

        assertNotNull(th);
        assertThat(th.getMessage()).isEqualTo("foo");
    }

    @Test
    public void testUtilsGet() throws Exception {
        final List<Fiber<String>> fibers = new ArrayList<>();
        final List<String> expectedResults = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int tmpI = i;
            expectedResults.add("testUtilsSequence-" + tmpI);
            fibers.add(new Fiber<>(() -> "testUtilsSequence-" + tmpI).start());
        }

        final List<String> results = FiberUtil.get(fibers);
        assertThat(results).isEqualTo(expectedResults);
    }

    @Test
    public void testUtilsGetWithTimeout() throws Exception {
        final List<Fiber<String>> fibers = new ArrayList<>();
        final List<String> expectedResults = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int tmpI = i;
            expectedResults.add("testUtilsSequence-" + tmpI);
            fibers.add(new Fiber<>(() -> "testUtilsSequence-" + tmpI).start());
        }

        final List<String> results = FiberUtil.get(1, TimeUnit.SECONDS, fibers);
        assertThat(results).isEqualTo(expectedResults);
    }

    @Test
    public void testUtilsGetZeroWait() throws Exception {
        final List<Fiber<String>> fibers = new ArrayList<>();
        final List<String> expectedResults = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int tmpI = i;
            expectedResults.add("testUtilsSequence-" + tmpI);
            fibers.add(new Fiber<>(() -> "testUtilsSequence-" + tmpI).start());
        }

        assertThrows(TimeoutException.class, () -> {
            final List<String> results = FiberUtil.get(0, TimeUnit.SECONDS, fibers);
            assertThat(results).isEqualTo(expectedResults);
        });
    }

    @Test
    public void testUtilsGetSmallWait() throws Exception {
        final List<Fiber<String>> fibers = new ArrayList<>();
        final List<String> expectedResults = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int tmpI = i;
            expectedResults.add("testUtilsSequence-" + tmpI);
            fibers.add(new Fiber<>(() -> {
                // increase the sleep time to simulate data coming in then timeout
                Strand.sleep(tmpI * 3, TimeUnit.MILLISECONDS);
                return "testUtilsSequence-" + tmpI;
            }).start());
        }

        assertThrows(TimeoutException.class, () -> {
            // must be less than 60 (3 * 20) or else the test could sometimes pass.
            final List<String> results = FiberUtil.get(55, TimeUnit.MILLISECONDS, fibers);
            assertThat(results).isEqualTo(expectedResults);
        });
    }

    @Test
    public void testSerialization1() throws Exception {
        // com.esotericsoftware.minlog.Log.set(1);
        final SettableFuture<byte[]> buf = new SettableFuture<>();

        /*Fiber<Integer> f1 = */ new SerFiber1(scheduler, new SettableFutureFiberWriter(buf)).start();
        Fiber<Integer> f2 = Fiber.unparkSerialized(buf.get(), scheduler);

        assertThat(f2.get()).isEqualTo(55);
    }

    static class SerFiber1 extends SerFiber<Integer> {
        public SerFiber1(FiberScheduler scheduler, FiberWriter fiberWriter) {
            super(scheduler, fiberWriter);
        }

        @Override
        public Integer run() throws SuspendExecution, InterruptedException {
            int sum = 0;
            for (int i = 1; i <= 10; i++) {
                sum += i;
                if (i == 5) {
                    Fiber.parkAndSerialize(fiberWriter);
                    assert sum == 15;
                }
            }
            return sum;
        }
    }

    @Test
    public void testSerialization2() throws Exception {
        // com.esotericsoftware.minlog.Log.set(1);
        final SettableFuture<byte[]> buf = new SettableFuture<>();

        Fiber<Integer> f1 = new SerFiber2(scheduler, new SettableFutureFiberWriter(buf)).start();
        Fiber<Integer> f2 = Fiber.unparkSerialized(buf.get(), scheduler);

        assertThat(f2.get()).isEqualTo(55);
    }

    static class SerFiber2 extends Fiber<Integer> {
        public SerFiber2(FiberScheduler scheduler, final FiberWriter fiberWriter) {
            super(scheduler, new SuspendableCallable<Integer>() {
                @Override
                public Integer run() throws SuspendExecution, InterruptedException {
                    int sum = 0;
                    for (int i = 1; i <= 10; i++) {
                        sum += i;
                        if (i == 5) {
                            Fiber.parkAndSerialize(fiberWriter);
                            assert sum == 15;
                        }
                    }
                    return sum;
                }
            });
        }
    }

    @Test
    public void testSerializationWithThreadLocals() throws Exception {
        final ThreadLocal<String> tl1 = new ThreadLocal<>();
        final InheritableThreadLocal<String> tl2 = new InheritableThreadLocal<>();
        tl1.set("foo");
        tl2.set("bar");

        final SettableFuture<byte[]> buf = new SettableFuture<>();

        /*Fiber<Integer> f1 = */new SerFiber3(scheduler, new SettableFutureFiberWriter(buf), tl1, tl2).start();
        Fiber<Integer> f2 = Fiber.unparkSerialized(buf.get(), scheduler);

        assertThat(f2.get()).isEqualTo(55);
    }

    static class SerFiber3 extends SerFiber<Integer> {
        private final ThreadLocal<String> tl1;
        private final InheritableThreadLocal<String> tl2;

        public SerFiber3(FiberScheduler scheduler, FiberWriter fiberWriter, ThreadLocal<String> tl1, InheritableThreadLocal<String> tl2) {
            super(scheduler, fiberWriter);
            this.tl1 = tl1;
            this.tl2 = tl2;
        }

        @Override
        public Integer run() throws SuspendExecution, InterruptedException {
            assertThat(tl1.get()).isNull();
            assertThat(tl2.get()).isEqualTo("bar");

            tl1.set("koko");
            tl2.set("bubu");

            int sum = 0;
            for (int i = 1; i <= 10; i++) {
                sum += i;
                if (i == 5) {
                    Fiber.parkAndSerialize(fiberWriter);
                    assert sum == 15;
                }
            }

            assertThat(tl1.get()).isEqualTo("koko");
            assertThat(tl2.get()).isEqualTo("bubu");
            return sum;
        }
    }

    static class SerFiber<V> extends Fiber<V> implements java.io.Serializable {
        protected final transient FiberWriter fiberWriter;

        public SerFiber(FiberScheduler scheduler, FiberWriter fiberWriter) {
            super(scheduler);
            this.fiberWriter = fiberWriter;
        }
    }

    @Test
    public void testCustomSerialization() throws Exception {
        // com.esotericsoftware.minlog.Log.set(1);
        final SettableFuture<byte[]> buf = new SettableFuture<>();

        /*Fiber<Integer> f1 = */new CustomSerFiber(scheduler, new SettableFutureCustomWriter(buf)).start();
        Fiber<Integer> f2 = Fiber.unparkSerialized(buf.get(), scheduler);

        assertThat(f2.get()).isEqualTo(55);
    }

    static class CustomSerFiber extends Fiber<Integer> implements Serializable {
        final private transient CustomFiberWriter writer;

        public CustomSerFiber(FiberScheduler scheduler, CustomFiberWriter writer) {
            super(scheduler);
            this.writer = writer;
        }

        @Override
        public Integer run() throws SuspendExecution, InterruptedException {
            int sum = 0;
            for (int i = 1; i <= 10; i++) {
                sum += i;
                if (i == 5) {
                    Fiber.parkAndCustomSerialize(writer);
                    assert sum == 15;
                }
            }
            return sum;
        }
    }

    static class SettableFutureCustomWriter implements CustomFiberWriter {
        final private transient SettableFuture<byte[]> buf;

        public SettableFutureCustomWriter(SettableFuture<byte[]> buf) {
            this.buf = buf;
        }

        @Override
        public void write(Fiber fiber) {
            buf.set(Fiber.getFiberSerializer().write(fiber));
        }
    }

    static class SettableFutureFiberWriter implements FiberWriter {
        private final transient SettableFuture<byte[]> buf;

        public SettableFutureFiberWriter(SettableFuture<byte[]> buf) {
            this.buf = buf;
        }

        @Override
        public void write(Fiber fiber, ByteArraySerializer ser) {
            buf.set(ser.write(fiber));
        }
    }
}
