/**
 * Copyright (c) 2011, 2014, Jonathan Giles, Johan Vos, Hendrik Ebbers
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *     * Neither the name of DataFX, the website javafxdata.org, nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL DataFX BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.datafx.core.concurrent;

import javafx.concurrent.Task;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The class defines a chain of processes. 
 * All processes will be running in a queue and the result of a process will be 
 * used as the input parameter for the next process.
 * 
 * @param <T>  Return value of the chain.
 *
 */
public class ProcessChain<T> {

    private List<ProcessDescription<?, ?>> processes;
    private Executor executorService;

    public ProcessChain() {
        this(Executors.newCachedThreadPool(), null);
    }

    public ProcessChain(Executor executorService) {
        this(executorService, null);
    }

    private ProcessChain(Executor executorService, List<ProcessDescription<?, ?>> processes) {
        this.executorService = executorService;
        this.processes = new ArrayList<>();
        if (processes != null) {
            this.processes.addAll(processes);
        }
    }

    public static ProcessChain<Void> create() {
        return new ProcessChain<>();
    }

    public static ProcessChain<Void> create(Executor executorService) {
        return new ProcessChain<>(executorService);
    }

    public <V> ProcessChain<V> addFunction(Function<T, V> function, ThreadType type) {
        processes.add(new ProcessDescription<T, V>(function, type));
        return new ProcessChain<V>(executorService, processes);
    }

    public <V> ProcessChain<V> addFunctionInPlatformThread(Function<T, V> function) {
        return addFunction(function, ThreadType.PLATFORM);
    }

    public <V> ProcessChain<V> addFunctionInExecutor(Function<T, V> function) {
        return addFunction(function, ThreadType.EXECUTOR);
    }

    public ProcessChain<Void> addRunnable(Runnable runnable, ThreadType type) {
           return addFunction((Function<T, Void>) (e) -> {
               runnable.run();
               return null;
           }, type);
    }

    public ProcessChain<Void> addRunnableInPlatformThread(Runnable runnable) {
        return addRunnable(runnable, ThreadType.PLATFORM);
    }

    public ProcessChain<Void> addRunnableInExecutor(Runnable runnable) {
        return addRunnable(runnable, ThreadType.EXECUTOR);
    }

    public ProcessChain<Void> addConsumer(Consumer<T> consumer, ThreadType type) {
        return addFunction((Function<T, Void>) (e) -> {
            consumer.accept(e);
            return null;
        }, type);
    }

    public ProcessChain<Void> addConsumerInPlatformThread(Consumer<T> consumer) {
        return addConsumer(consumer, ThreadType.PLATFORM);
    }

    public ProcessChain<Void> addConsumerInExecutor(Consumer<T> consumer) {
        return addConsumer(consumer, ThreadType.EXECUTOR);
    }

    public <V> ProcessChain<V> addSupplierInPlatformThread(Supplier<V> supplier) {
        return addSupplier(supplier, ThreadType.PLATFORM);
    }

    public <V> ProcessChain<V> addSupplierInExecutor(Supplier<V> supplier) {
        return addSupplier(supplier, ThreadType.EXECUTOR);
    }

    public <V> ProcessChain<V> addSupplier(Supplier<V> supplier, ThreadType type) {
        return addFunction((Function<T, V>) (e) -> {
            return supplier.get();
        }, type);
    }

    @SuppressWarnings("unchecked")
    private <U, V> V execute(U inputParameter, ProcessDescription<U, V> processDescription, Executor executorService) throws InterruptedException, ExecutionException {
        if (processDescription.getThreadType().equals(ThreadType.EXECUTOR)) {
            FutureTask<V> task = new FutureTask<V>(() -> {
                return processDescription.getFunction().apply(inputParameter);
            });
            executorService.execute(task);
            return task.get();
        } else {
            return ConcurrentUtils.runCallableAndWait(() -> {
                return processDescription.getFunction().apply(inputParameter);
            });
        }
    }

    public Task<T> repeatInfinite() {
        return repeat(Integer.MAX_VALUE);
    }

    public Task<T> repeatInfinite(Duration pauseTime) {
        return repeat(Integer.MAX_VALUE, pauseTime);
    }

    public Task<T> repeat(int count) {
        return repeat(count, Duration.ZERO);
    }

    public Task<T> repeat(int count, Duration pauseTime) {
        Task<T> task = new Task<T>() {

            @Override
            protected T call() throws Exception {
                Object lastResult = null;
                if(count == Integer.MAX_VALUE) {
                    while(true) {
                        lastResult = null;
                        for (ProcessDescription<?, ?> processDescription : processes) {
                            lastResult = execute(lastResult, (ProcessDescription<Object, ?>) processDescription, executorService);
                        }
                        Thread.sleep((long) pauseTime.toMillis());
                    }
                } else {
                    for(int i = 0; i < count; i++) {
                        lastResult = null;
                        for (ProcessDescription<?, ?> processDescription : processes) {
                            lastResult = execute(lastResult, (ProcessDescription<Object, ?>) processDescription, executorService);
                        }
                        Thread.sleep((long) pauseTime.toMillis());
                    }
                }
                return (T) lastResult;
            }

        };
        executorService.execute(task);
        return task;
    }

    public Task<T> run() {
        Task<T> task = new Task<T>() {

            @Override
            protected T call() throws Exception {
                Object lastResult = null;
                for (ProcessDescription<?, ?> processDescription : processes) {
                    lastResult = execute(lastResult, (ProcessDescription<Object, ?>) processDescription, executorService);
                }
                return (T) lastResult;
            }

        };
        executorService.execute(task);
        return task;
    }
}