package com.sandship.stability.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableAsync
public class StabilityExecutorConfig {

    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();

    @Bean(name = "stabilityExecutor")
    public Executor stabilityExecutor() {
        int corePoolSize = Math.max(2, CPU_CORES / 2);
        int maxPoolSize = Math.max(4, CPU_CORES);
        int queueCapacity = 500;

        log.info("[StabilityExecutor] 初始化稳性计算线程池 - 核心: {}, 最大: {}, 队列: {}",
                corePoolSize, maxPoolSize, queueCapacity);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("stability-calc-" + t.getId());
                    t.setDaemon(false);
                    t.setPriority(Thread.NORM_PRIORITY + 1);
                    t.setUncaughtExceptionHandler(
                            (thread, ex) -> log.error("[StabilityExecutor] 线程 {} 异常: {}",
                                    thread.getName(), ex.getMessage(), ex));
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @Bean(name = "stormSimExecutor")
    public Executor stormSimExecutor() {
        int corePoolSize = Math.max(1, CPU_CORES / 4);
        int maxPoolSize = Math.max(2, CPU_CORES / 2);
        int queueCapacity = 200;

        log.info("[StormSimExecutor] 初始化风暴模拟线程池 - 核心: {}, 最大: {}, 队列: {}",
                corePoolSize, maxPoolSize, queueCapacity);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                120L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("storm-sim-" + t.getId());
                    t.setDaemon(false);
                    t.setUncaughtExceptionHandler(
                            (thread, ex) -> log.error("[StormSimExecutor] 线程 {} 异常: {}",
                                    thread.getName(), ex.getMessage(), ex));
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @Bean(name = "loadingOptimizerExecutor")
    public Executor loadingOptimizerExecutor() {
        int corePoolSize = Math.max(1, CPU_CORES / 4);
        int maxPoolSize = Math.max(2, CPU_CORES / 2);
        int queueCapacity = 100;

        log.info("[LoadingOptimizerExecutor] 初始化装载优化线程池 - 核心: {}, 最大: {}, 队列: {}",
                corePoolSize, maxPoolSize, queueCapacity);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                300L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("loading-opt-" + t.getId());
                    t.setDaemon(false);
                    t.setUncaughtExceptionHandler(
                            (thread, ex) -> log.error("[LoadingOptimizerExecutor] 线程 {} 异常: {}",
                                    thread.getName(), ex.getMessage(), ex));
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
