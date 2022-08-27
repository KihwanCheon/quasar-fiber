package example.quasar.sample;

import co.paralleluniverse.fibers.*;
import co.paralleluniverse.strands.SuspendableRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <a href="https://docs.paralleluniverse.co/quasar/#examples">quasar site</a>
 * <a href="https://github.com/puniverse/quasar/blob/master/quasar-core/src/test/java/co/paralleluniverse/fibers/FiberAsyncTest.java">github</a>
 */
public class FiberAsyncTest {

    private FiberScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new FiberForkJoinScheduler("test", 4, null, false);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    interface MyCallback {
        void call(String str);

        void fail(RuntimeException e);
    }

    interface Service {
        void registerCallback(MyCallback callback);
    }
    final ExecutorService executor = Executors.newFixedThreadPool(1);

    final Service syncService = callback -> callback.call("sync result!");

    final Service badSyncService = cb -> cb.fail(new RuntimeException("sync exception!"));

    final Service asyncService = callback -> executor.submit(() -> {
        try {
            Thread.sleep(20);
            callback.call("async result!");
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    });

    final Service longAsyncService = callback -> executor.submit(() -> {
        try {
            Thread.sleep(2000);
            callback.call("async result!");
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    });

    final Service badAsyncService = callback -> executor.submit(() -> {
        try {
            Thread.sleep(20);
            callback.fail(new RuntimeException("async exception!"));
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    });

    static String callService(Service service) throws SuspendExecution, InterruptedException {
        return new MyFiberAsync() {
            @Override protected void requestAsync() {
                service.registerCallback(this);
            }
        }.run();
    }

    static String callService(Service service, long timeout) throws SuspendExecution, InterruptedException, TimeoutException {
        return new MyFiberAsync() {
            @Override protected void requestAsync() {
                service.registerCallback(this);
            }
        }.run(timeout, TimeUnit.MICROSECONDS);
    }

    static abstract class MyFiberAsync extends FiberAsync<String, RuntimeException> implements MyCallback {
        @SuppressWarnings({"unused"})
        public final Fiber<?> fiber;

        public MyFiberAsync() {
            this.fiber = Fiber.currentFiber();
        }

        @Override
        public void call(String str) {
            super.asyncCompleted(str);
        }

        @Override
        public void fail(RuntimeException e) {
            super.asyncFailed(e);
        }
    }

    @Test
    public void testSyncCallback() throws Exception {
        final var fiber = new Fiber<>(scheduler, () -> {
            String res = callService(syncService);
            assertThat(res).isEqualTo("sync result!");
        }).start();

        fiber.join();
    }

    @Test
    public void testSyncCallbackException() throws Exception {
        final var fiber = new Fiber<>(scheduler, () -> {
            try {
                @SuppressWarnings("unused")
                String res = callService(badSyncService);
                fail();
            } catch (Exception e) {
                assertThat(e.getMessage()).isEqualTo("sync exception!");
            }
        }).start();

        fiber.join();
    }

    @Test
    public void testAsyncCallback() throws Exception {
        final var fiber = new Fiber<>(scheduler, () -> {
            String res = callService(asyncService);
            assertThat(res).isEqualTo("async result!");
        }).start();

        fiber.join();
    }

    @Test
    public void testAsyncCallbackException() throws Exception {
        final var fiber = new Fiber<>(scheduler, () -> {
            try {
                @SuppressWarnings("unused")
                String res = callService(badAsyncService);
                fail();
            } catch (Exception e) {
                assertThat(e.getMessage()).isEqualTo("async exception!");
            }
        }).start();

        fiber.join();
    }

    @Test
    public void testAsyncCallbackExceptionInRequestAsync() throws Exception {
        final var fiber = new Fiber<>(scheduler, () -> {
            try {
                new FiberAsync<String, RuntimeException>() {

                    @Override
                    protected void requestAsync() {
                        throw new RuntimeException("requestAsync exception!");
                    }

                }.run();
                fail();
            } catch (Exception e) {
                assertThat(e.getMessage()).isEqualTo("requestAsync exception!");
            }
        }).start();

        fiber.join();
    }

    @Test
    public void testTimedAsyncCallbackNoTimeout() throws Exception {
        final var fiber = new Fiber<>(scheduler, () -> {
            try {
                String res = callService(asyncService, 50);
                assertThat(res).isEqualTo("async result!");
            } catch (TimeoutException e) {
                throw new RuntimeException();
            }
        }).start();

        fiber.join();
    }

    @Test
    public void testTimedAsyncCallbackWithTimeout() throws Exception {
        final var fiber = new Fiber<>(scheduler, () -> {
            try {
                @SuppressWarnings("unused")
                String res = callService(asyncService, 10);
                fail();
            } catch (TimeoutException e) {
            }
        }).start();

        fiber.join();
    }

    @Test
    public void testInterrupt1() throws Exception {
        final var fiber = new Fiber<>(scheduler, () -> {
            try {
                callService(longAsyncService);
                fail();
            } catch (InterruptedException e) {
            }
        }).start();

        fiber.interrupt();
        fiber.join();
    }

    @Test
    public void testInterrupt2() throws Exception {
        final var fiber = new Fiber<>(scheduler, () -> {
            try {
                callService(longAsyncService);
                fail();
            } catch (InterruptedException e) {
            }
        }).start();

        Thread.sleep(100);
        fiber.interrupt();
        fiber.join();
    }

    @Test
    public void whenCancelRunBlockingInterruptExecutingThread() throws Exception {
        final AtomicBoolean started = new AtomicBoolean();
        final AtomicBoolean interrupted = new AtomicBoolean();

        var fiber = new Fiber<>((SuspendableRunnable) () ->
                FiberAsync.runBlocking(Executors.newSingleThreadExecutor(),
                () -> {
                    started.set(true);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        interrupted.set(true);
                    }
                    return null;
                }));

        fiber.start();
        Thread.sleep(100);
        fiber.cancel(true);
        try {
            fiber.join(5, TimeUnit.MILLISECONDS);
            fail("InterruptedException not thrown");
        } catch(ExecutionException e) {
            if (!(e.getCause() instanceof InterruptedException))
                fail("InterruptedException not thrown");
        }
        Thread.sleep(100);
        assertThat(started.get()).isEqualTo(true);
        assertThat(interrupted.get()).isEqualTo(true);
    }

    @Test
    public void testRunBlocking() throws Exception {
        final var fiber = new Fiber<>(() -> {
            String res = FiberAsync.runBlocking(Executors.newCachedThreadPool(), () -> {
                Thread.sleep(300);
                return "ok";
            });
            assertThat(res).isEqualTo("ok");
        }).start();

        fiber.join();
    }

    @Test
    public void testRunBlockingWithTimeout1() throws Exception {
        final var fiber = new Fiber<>(() -> {
            try {
                String res = FiberAsync.runBlocking(Executors.newCachedThreadPool(), 400, TimeUnit.MILLISECONDS, () -> {
                    Thread.sleep(300);
                    return "ok";
                });
                assertThat(res).isEqualTo("ok");
            } catch (TimeoutException e) {
                fail();
            }
        }).start();

        fiber.join();
    }

    @Test
    public void testRunBlockingWithTimeout2() throws Exception {
        final var fiber = new Fiber<>(() -> {
            try {
                @SuppressWarnings("unused")
                String res = FiberAsync.runBlocking(Executors.newCachedThreadPool(), 100, TimeUnit.MILLISECONDS, () -> {
                    Thread.sleep(300);
                    return "ok";
                });
                fail();
            } catch (TimeoutException e) {
            }
        }).start();

        fiber.join();
    }
}
