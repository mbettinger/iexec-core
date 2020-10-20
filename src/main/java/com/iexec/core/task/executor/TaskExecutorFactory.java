/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.task.executor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;

/**
 * A factory that manages expiring thread executors.
 * Each executor has its own expiration period.
 */
@Component
class TaskExecutorFactory {

    // this map is thread-safe
    private final ExpiringMap<String, ThreadPoolExecutor> map;

    TaskExecutorFactory() {
        this.map = ExpiringMap.builder()
                .expirationPolicy(ExpirationPolicy.CREATED)
                .variableExpiration()
                // shutdown thread executor when an entry expires
                .expirationListener(shutdownExecutorWhenExpired())
                .build();
    }

    /**
     * Get a task's executor or create a new
     * one if needed.
     * 
     * @param chainTaskId id associated to executor
     * @param maxTtl max time to live for this executor
     * @return the executor
     */
    Executor getOrCreate(String chainTaskId) {
        return getOrCreate(chainTaskId, 1000000000);
    }

    Executor getOrCreate(String chainTaskId, long expiration) {
        if (map.containsKey(chainTaskId)) {
            return map.get(chainTaskId);
        }
        map.put(chainTaskId,
                new SingleThreadExecutorWithFixedSizeQueue(1));
        map.setExpiration(chainTaskId, expiration, MILLISECONDS);
        return map.get(chainTaskId);
    }

    /**
     * Set the expiration period of an executor
     * to 0 and the ExpirationListener will do
     * the rest.
     * 
     * @param chainTaskId
     */
    void remove(String chainTaskId) {
        map.setExpiration(chainTaskId, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * The max TTL of an executor is the max duration
     * of an iExec task in a given iExec category.
     * The executor can expire before the max duration
     * once the task is COMPLETED.
     * 
     * @return a listener that shuts down the executor.
     */
    private ExpirationListener<String,ThreadPoolExecutor> 
            shutdownExecutorWhenExpired() {
        return new ExpirationListener<String,ThreadPoolExecutor>() {
            @Override
            public void expired(String chainTaskId,
                    ThreadPoolExecutor executor) {
                executor.shutdown();
            }
        };
    }
}
