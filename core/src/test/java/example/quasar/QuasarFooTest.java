package example.quasar;

import co.paralleluniverse.concurrent.util.AbstractCompletableExecutorService;
import co.paralleluniverse.concurrent.util.CompletableExecutors;
import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor;
import co.paralleluniverse.fibers.*;
import co.paralleluniverse.strands.Strand;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * https://docs.paralleluniverse.co/quasar/
 */
@Slf4j
public class QuasarFooTest {

    interface FooCompletion {
        void success(String result);
        void failure(FooException exception);
        class FooException extends RuntimeException { }
    }

    abstract static class FooAsync extends FiberAsync<String, FooCompletion.FooException> implements FooCompletion {
        private final int id;
        public FooAsync(int id) {
            this.id = id;
        }

        @Override
        public void success(String result) {
            log.info("id: {} -- result: {}", id, result);
            asyncCompleted(result);
        }

        @Override
        public void failure(FooException exception) {
            log.info("id: {}", id);
            asyncFailed(exception);
        }
    }

    public static class IdGen implements Supplier<Integer> {
        AtomicInteger id = new AtomicInteger();
        @Override
        public Integer get() {return id.incrementAndGet();}
    };

    @Test
    void each_sequential_FiberUtil_runInFiber() {
        log.info("test async");
        try {
            IdGen idGen = new IdGen();
            log.info("{}", FiberUtil.runInFiber(() -> this.op(idGen.get())));
            log.info("{}", FiberUtil.runInFiber(() -> this.op(idGen.get())));
            log.info("{}", FiberUtil.runInFiber(() -> this.op(idGen.get())));
            log.info("{}", FiberUtil.runInFiber(() -> this.op(idGen.get())));
            log.info("{}", FiberUtil.runInFiber(() -> this.op(idGen.get())));
        } catch (InterruptedException | ExecutionException e) {
            log.error("error", e);
        }
    }

    @Test
    void async_FiberScheduler_newFiber() {
        log.info("test async");
        IdGen idGen = new IdGen();
        List<Fiber<String>> fibers = new ArrayList<>(10);
//        FiberScheduler instance = DefaultFiberScheduler.getInstance(); // fork join
        FiberScheduler instance = new FiberForkJoinScheduler("test", 4, null, false);
//        FiberScheduler instance = new FiberExecutorScheduler("test fiber scheduler", CompletableExecutors.completableDecorator(new ScheduledSingleThreadExecutor()));
//        FiberScheduler instance = new FiberExecutorScheduler("test fiber scheduler", MoreExecutors.directExecutor());
        for (int i = 0; i < 10; ++i) {
            fibers.add(instance.newFiber(() -> this.op(idGen.get())));
        }

        log.info("test job create and start all");
        fibers.forEach(Fiber::start);

        log.info("call join");
        fibers.forEach(f -> {
            try {
                f.join();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    String op(int id) throws SuspendExecution, InterruptedException {
        Fiber.yield();
        log.info("op {}", id);
        return new FooAsync(id) {
            @Override
            protected void requestAsync() {
                Foo.asyncOp(this);
            }
        }.run();
    }

    public static class Foo {
        public static void asyncOp(FooAsync async) {
            log.info("asyncOp: {} {}", async.id, Thread.currentThread().getName());
            Random rnd = new Random();
            long millis = rnd.nextInt(1000);
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            async.success( "success---" + millis);
        }
    }
}
