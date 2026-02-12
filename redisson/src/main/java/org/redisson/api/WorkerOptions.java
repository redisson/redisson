/**
 * Copyright (c) 2013-2026 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.redisson.api.executor.TaskListener;
import org.redisson.config.Config;
import org.redisson.executor.SpringTasksInjector;
import org.redisson.executor.TasksInjector;
import org.springframework.beans.factory.BeanFactory;

/**
 * Configuration for RExecutorService workers.
 * 
 * @author Nikita Koksharov
 *
 */
public final class WorkerOptions {

    private int workers = 1;
    private ExecutorService executorService;
    private TasksInjector tasksInjector;
    private long taskTimeout;
    private List<TaskListener> listeners = new ArrayList<>();
    
    private WorkerOptions() {
    }
    
    public static WorkerOptions defaults() {
        return new WorkerOptions();
    }
    
    public int getWorkers() {
        return workers;
    }

    /**
     * Defines workers amount used to execute tasks.
     * Default is <code>1</code>.
     * 
     * @param workers - workers amount
     * @return self instance
     */
    public WorkerOptions workers(int workers) {
        this.workers = workers;
        return this;
    }
    
    /**
     * Use {@code tasksInjector(new SpringTasksInjector(beanFactory)} instead.
     *
     * @param beanFactory - Spring BeanFactory instance
     * @return self instance
     */
    @Deprecated
    public WorkerOptions beanFactory(BeanFactory beanFactory) {
        if (beanFactory != null) {
            this.tasksInjector = new SpringTasksInjector(beanFactory);
        }
        return this;
    }

    /**
     * Defines TasksInjector instance to execute tasks with CDI.
     * For example, Spring's '@Autowired', '@Value' or JSR-330's '@Inject' annotation.
     *
     * @param tasksInjector task
     * @return self instance
     *
     * @see SpringTasksInjector
     */
    public WorkerOptions tasksInjector(TasksInjector tasksInjector) {
        this.tasksInjector = tasksInjector;
        return this;
    }

    public TasksInjector getTasksInjector() {
        return tasksInjector;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }
    
    /**
     * Defines custom ExecutorService to execute tasks.
     * {@link Config#setExecutor(ExecutorService)} is used by default.
     * 
     * @param executorService - custom ExecutorService
     * @return self instance
     */
    public WorkerOptions executorService(ExecutorService executorService) {
        this.executorService = executorService;
        return this;
    }

    /**
     * Defines task timeout since task execution start moment
     *
     * @param timeout - timeout of task
     * @param unit - time unit
     * @return self instance
     */
    public WorkerOptions taskTimeout(long timeout, TimeUnit unit) {
        this.taskTimeout = unit.toMillis(timeout);
        return this;
    }

    public long getTaskTimeout() {
        return taskTimeout;
    }

    /**
     * Adds task listener
     *
     * @see org.redisson.api.executor.TaskSuccessListener
     * @see org.redisson.api.executor.TaskFailureListener
     * @see org.redisson.api.executor.TaskStartedListener
     * @see org.redisson.api.executor.TaskFinishedListener
     *
     * @param listener - task listener
     * @return self instance
     */
    public WorkerOptions addListener(TaskListener listener) {
        listeners.add(listener);
        return this;
    }

    public List<TaskListener> getListeners() {
        return listeners;
    }
}
