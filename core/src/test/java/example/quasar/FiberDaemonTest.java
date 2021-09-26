package example.quasar;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.FiberForkJoinScheduler;
import co.paralleluniverse.fibers.FiberScheduler;
import co.paralleluniverse.strands.Strand;
import co.paralleluniverse.strands.SuspendableCallable;
import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

@Slf4j
public class FiberDaemonTest {

    static boolean stop = false;
    static Random rnd = new Random();

    @Test
    void fiber_sleep() throws ExecutionException, InterruptedException {

        FiberScheduler scheduler = new FiberForkJoinScheduler("test", 1, null, false);
        Stopwatch stopwatch = Stopwatch.createStarted();

        Fiber<Void> f = scheduler.newFiber((SuspendableCallable<Void>) () -> {
            while(stopwatch.elapsed().getSeconds() < 1) {
                List<Fiber<Void>> fiberList = new ArrayList<>();
                for (int i = 0; i < 1000000; ++i) {
                    if (i % 100000 == 0) {
                        log.info("{}", i);
                    }
                    int r = rnd.nextInt(1000000);
                    int ri = i;
                    if (r < 10) {
                        log.info("random gen {}", r);
                        fiberList.add(scheduler.newFiber(() -> {
                            log.info("random exec before {} - {}", ri, r);

                            scheduler.newFiber(() -> {log.info("random exec inner {} - {}", ri, r); return null;}).start();

                            Strand.yield();
//                            Strand.sleep(1L);
                            log.info("random exec after {} - {}", ri, r);
                            return null;
                        }));
                    }
                }

                fiberList.forEach(Fiber::start);

                Strand.sleep(1L);
            }

            return null;
        });
        f.start();
        f.join();
        scheduler.shutdown();
    }
}
