package io.smallrye.faulttolerance;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Provider of thread pools for timeouts and asynchronous invocations
 *
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 */
@Singleton
public class ExecutorProvider {

    @Inject
    @ConfigProperty(name = "io.smallrye.faulttolerance.globalThreadPoolSize", defaultValue = "100")
    private Integer size;

    @Inject
    @ConfigProperty(name = "io.smallrye.faulttolerance.globalThreadPoolQueueSize")
    private Optional<Integer> queueSize;

    @Inject
    @ConfigProperty(name = "io.smallrye.faulttolerance.timeoutExecutorThreads", defaultValue = "5")
    private Integer timeoutExecutorSize;

    private ExecutorService globalExecutor;

    private ScheduledExecutorService timeoutExecutor;

    private ExecutorFactory executorFactory;

    private final List<ExecutorService> allExecutors = new ArrayList<>();

    @PostConstruct
    public void setUp() {
        executorFactory = executorProvider();
        globalExecutor = executorFactory.createExecutorService(size, queueSize.orElse(size));
        timeoutExecutor = executorFactory.createTimeoutExecutor(timeoutExecutorSize);
        allExecutors.add(globalExecutor);
        allExecutors.add(timeoutExecutor);
    }

    void tearDown(@Observes @BeforeDestroyed(ApplicationScoped.class) Object ignored) {
        allExecutors.forEach(ExecutorService::shutdown);
    }

    public ExecutorService getAdHocExecutor(int size, int queueSize, BlockingQueue<Runnable> queue) {
        ExecutorService executor = executorFactory.createExecutorService(size, queueSize, queue);
        allExecutors.add(executor);
        return executor;
    }

    public ExecutorService getGlobalExecutor() {
        return globalExecutor;
    }

    public ScheduledExecutorService getTimeoutExecutor() {
        return timeoutExecutor;
    }

    private static ExecutorFactory executorProvider() {
        ServiceLoader<ExecutorFactory> loader = ServiceLoader.load(ExecutorFactory.class);

        Iterator<ExecutorFactory> iterator = loader.iterator();

        ExecutorFactory maxPriorityProvider = new DefaultExecutorFactory();
        while (iterator.hasNext()) {
            ExecutorFactory next = iterator.next();
            if (next.priority() > maxPriorityProvider.priority()) {
                maxPriorityProvider = next;
            }
        }
        return maxPriorityProvider;
    }
}
