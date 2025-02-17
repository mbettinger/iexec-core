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

package com.iexec.core.task;

import com.iexec.common.chain.*;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.utils.BytesUtils;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.chain.adapter.BlockchainAdapterService;
import com.iexec.core.configuration.ResultRepositoryConfiguration;
import com.iexec.core.detector.replicate.RevealTimeoutDetector;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.common.utils.DateTimeUtils;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import com.iexec.core.worker.WorkerService;
import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.iexec.core.task.TaskStatus.*;
import static com.iexec.common.utils.DateTimeUtils.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// TODO
public class TaskServiceTests {

    private final static String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

    private final static String CHAIN_DEAL_ID = "0xd82223e5feff6720792ffed1665e980da95e5d32b177332013eaba8edc07f31c";
    private final static String CHAIN_TASK_ID = "0x65bc5e94ed1486b940bd6cc0013c418efad58a0a52a3d08cee89faaa21970426";

    private final static String DAPP_NAME = "dappName";
    private final static String COMMAND_LINE = "commandLine";
    private final long maxExecutionTime = 60000;
    private final Date contributionDeadline = new Date();
    private final Date finalDeadline = new Date();
    private final static String NO_TEE_TAG = BytesUtils.EMPTY_HEXASTRING_64;
    private final static String RESULT_LINK = "/ipfs/the_result_string";

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskUpdateRequestManager updateRequestManager;

    @Mock
    private WorkerService workerService;

    @Mock
    private IexecHubService iexecHubService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private ResultRepositoryConfiguration resulRepositoryConfig;

    @Mock
    private Web3jService web3jService;

    @Mock
    private RevealTimeoutDetector revealTimeoutDetector;

    @Mock
    private BlockchainAdapterService blockchainAdapterService;

    @InjectMocks
    private TaskService taskService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    private Task getStubTask() {
        Task task = new Task(CHAIN_DEAL_ID, 0, DAPP_NAME, COMMAND_LINE, 0, maxExecutionTime, NO_TEE_TAG);
        task.setFinalDeadline(Date.from(Instant.now().plus(1, ChronoUnit.MINUTES)));
        return  task;
    }

    @Test
    public void shouldNotgetTaskWithTrust() {
        when(taskRepository.findByChainTaskId("dummyId")).thenReturn(Optional.empty());
        Optional<Task> task = taskService.getTaskByChainTaskId("dummyId");
        assertThat(task.isPresent()).isFalse();
    }

    @Test
    public void shouldGetOneTask() {
        Task task = getStubTask();
        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        Optional<Task> optional = taskService.getTaskByChainTaskId(CHAIN_TASK_ID);

        assertThat(optional.isPresent()).isTrue();
        assertThat(optional).isEqualTo(Optional.of(task));
    }

    @Test
    public void shouldAddTask() {
        Task task = getStubTask();
        task.changeStatus(TaskStatus.INITIALIZED);

        when(taskRepository.save(any())).thenReturn(task);
        Optional<Task> saved = taskService.addTask(CHAIN_DEAL_ID, 0, 0, DAPP_NAME, COMMAND_LINE,
                2, maxExecutionTime, "0x0", contributionDeadline, finalDeadline);
        assertThat(saved).isPresent();
        assertThat(saved).isEqualTo(Optional.of(task));
    }

    @Test
    public void shouldNotAddTask() {
        Task task = getStubTask();
        task.changeStatus(TaskStatus.INITIALIZED);
        when(taskRepository.findByChainDealIdAndTaskIndex(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(task));
        Optional<Task> saved = taskService.addTask(CHAIN_DEAL_ID, 0, 0, DAPP_NAME, COMMAND_LINE,
                2, maxExecutionTime, "0x0", contributionDeadline, finalDeadline);
        assertThat(saved).isEqualTo(Optional.empty());
    }

    @Test
    public void shouldFindByCurrentStatus() {
        TaskStatus status = TaskStatus.INITIALIZED;

        Task task = getStubTask();
        task.changeStatus(status);

        List<Task> taskList = new ArrayList<>();
        taskList.add(task);

        when(taskRepository.findByCurrentStatus(status)).thenReturn(taskList);

        List<Task> foundTasks = taskService.findByCurrentStatus(status);

        assertThat(foundTasks).isEqualTo(taskList);
        assertThat(foundTasks.get(0).getCurrentStatus()).isEqualTo(status);
    }

    @Test
    public void shouldNotFindByCurrentStatus() {
        TaskStatus status = TaskStatus.INITIALIZED;
        when(taskRepository.findByCurrentStatus(status)).thenReturn(Collections.emptyList());

        List<Task> foundTasks = taskService.findByCurrentStatus(status);

        assertThat(foundTasks).isEmpty();
    }

    @Test
    public void shouldFindByCurrentStatusList() {
        List<TaskStatus> statusList = Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.COMPLETED);

        Task task = getStubTask();
        task.changeStatus(TaskStatus.INITIALIZED);

        List<Task> taskList = new ArrayList<>();
        taskList.add(task);

        when(taskRepository.findByCurrentStatus(statusList)).thenReturn(taskList);

        List<Task> foundTasks = taskService.findByCurrentStatus(statusList);

        assertThat(foundTasks).isEqualTo(taskList);
        assertThat(foundTasks.get(0).getCurrentStatus()).isIn(statusList);
    }

    @Test
    public void shouldNotFindByCurrentStatusList() {
        List<TaskStatus> statusList = Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.COMPLETED);
        when(taskRepository.findByCurrentStatus(statusList)).thenReturn(Collections.emptyList());

        List<Task> foundTasks = taskService.findByCurrentStatus(statusList);

        assertThat(foundTasks).isEmpty();
    }


    @Test
    public void shouldGetInitializedOrRunningTasks() {
        List<Task> tasks = Collections.singletonList(mock(Task.class));
        when(taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(tasks);
        Assertions.assertThat(taskService.getInitializedOrRunningTasks())
                .isEqualTo(tasks);
    }

    @Test
    public void shouldGetTasksInNonFinalStatuses() {
        List<Task> tasks = Collections.singletonList(mock(Task.class));
        when(taskRepository.findByCurrentStatusNotIn(TaskStatus.getFinalStatuses()))
                .thenReturn(tasks);
        Assertions.assertThat(taskService.getTasksInNonFinalStatuses())
                .isEqualTo(tasks);
    }

    @Test
    public void shouldGetTasksWhereFinalDeadlineIsPossible() {
        List<Task> tasks = Collections.singletonList(mock(Task.class));
        when(taskRepository.findByCurrentStatusNotIn(TaskStatus.getStatusesWhereFinalDeadlineIsImpossible()))
                .thenReturn(tasks);
        Assertions.assertThat(taskService.getTasksWhereFinalDeadlineIsPossible())
                .isEqualTo(tasks);
    }

    @Test
    public void shouldGetChainTaskIdsOfTasksExpiredBefore() {
        Date date = new Date();
        Task task = mock(Task.class);
        when(task.getChainTaskId()).thenReturn(CHAIN_TASK_ID);
        List<Task> tasks = Collections.singletonList(task);
        when(taskRepository.findChainTaskIdsByFinalDeadlineBefore(date))
                .thenReturn(tasks);
        Assertions.assertThat(taskService.getChainTaskIdsOfTasksExpiredBefore(date))
                .isEqualTo(Collections.singletonList(CHAIN_TASK_ID));
    }

    // isExpired

    @Test
    public void shouldFindTaskExpired() {
        Task task = getStubTask();
        task.setFinalDeadline(Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)));
        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID))
                .thenReturn(Optional.of(task));

        assertThat(taskService.isExpired(CHAIN_TASK_ID)).isTrue();
    }

    // updateTask


    @Test
    public void shouldTriggerUpdateTaskAsynchronously() {
        taskService.updateTask(CHAIN_TASK_ID);
        verify(updateRequestManager).publishRequest(eq(CHAIN_TASK_ID));
    }

    // Tests on consensusReached2Reopening transition

    @Test
    public void shouldNotUpgrade2ReopenedSinceCurrentStatusWrong() {
        Task task = getStubTask();

        task.changeStatus(RECEIVED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(new ChainReceipt()));

        taskService.consensusReached2Reopening(task);

        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    @Test
    public void shouldNotUpgrade2ReopenedSinceNotAfterRevealDeadline() {
        Task task = getStubTask();

        task.changeStatus(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() + 100));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(new ChainReceipt()));

        taskService.consensusReached2Reopening(task);

        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    @Test
    public void shouldNotUpgrade2ReopenedSinceNotWeHaveSomeRevealed() {
        Task task = getStubTask();

        task.changeStatus(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(1);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(new ChainReceipt()));

        taskService.consensusReached2Reopening(task);

        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    @Test
    public void shouldNotUpgrade2ReopenedSinceCantReopenOnChain() {
        Task task = getStubTask();

        task.changeStatus(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(false);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(new ChainReceipt()));

        taskService.consensusReached2Reopening(task);

        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    @Test
    public void shouldNotUpgrade2ReopenedSinceNotEnoughtGas() {
        Task task = getStubTask();

        task.changeStatus(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(false);
        when(taskRepository.save(task)).thenReturn(task);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(new ChainReceipt()));

        taskService.consensusReached2Reopening(task);

        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }


    @Test
    public void shouldNotUpgrade2ReopenedBut2ReopendedFailedSinceTxFailed() {
        Task task = getStubTask();

        task.changeStatus(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.empty());

        taskService.consensusReached2Reopening(task);

        assertThat(task.getLastButOneStatus()).isEqualTo(REOPEN_FAILED);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    //TODO: Update reopen call
    //@Test
    public void shouldUpgrade2Reopened() {
        Task task = getStubTask();

        task.changeStatus(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(new ChainReceipt()));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.ACTIVE)
                .build()));
        doNothing().when(revealTimeoutDetector).detect();

        taskService.consensusReached2Reopening(task);

        assertThat(task.getDateStatusList().get(0).getStatus()).isEqualTo(RECEIVED);
        assertThat(task.getDateStatusList().get(1).getStatus()).isEqualTo(CONSENSUS_REACHED);
        assertThat(task.getDateStatusList().get(2).getStatus()).isEqualTo(REOPENING);
        assertThat(task.getDateStatusList().get(3).getStatus()).isEqualTo(REOPENED);
        assertThat(task.getDateStatusList().get(4).getStatus()).isEqualTo(INITIALIZED);
    }

    // Tests on received2Initializing transition

    @Test
    public void shouldNotUpdateReceived2InitializingSinceChainTaskIdIsNotEmpty() {
        Task task = getStubTask();
        task.changeStatus(RECEIVED);

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    // not sure if a "shouldNotUpdateReceived2InitializingSinceCurrentStatusIsNotReceived" test
    // is required

    @Test
    public void shouldNotUpdateReceived2InitializingSinceNoEnoughGas() {
        Task task = getStubTask();
        task.changeStatus(RECEIVED);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(false);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(true);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 1)).thenReturn(Optional.of(CHAIN_TASK_ID));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    @Test
    public void shouldNotUpdateReceived2InitializingSinceTaskNotInUnsetStatusOnChain() {
        Task task = getStubTask();
        task.changeStatus(RECEIVED);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(false);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(CHAIN_TASK_ID));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    @Test
    public void shouldNotUpdateReceived2InitializingSinceAfterContributionDeadline() {
        Task task = getStubTask();
        task.changeStatus(RECEIVED);
        task.setChainTaskId(CHAIN_TASK_ID);
        Pair<String, ChainReceipt> pair = Pair.of(CHAIN_TASK_ID, null);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(true);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(false);
        when(taskRepository.save(task)).thenReturn(task);
        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(CHAIN_TASK_ID));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    @Test
    public void shouldUpdateInitializing2InitailizeFailedSinceChainTaskIdIsEmpty() {
        Task task = getStubTask();
        task.changeStatus(RECEIVED);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(true);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.empty());

        taskService.updateTaskRunnable(task.getChainTaskId());

        assertThat(task.getLastButOneStatus()).isEqualTo(INITIALIZE_FAILED);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    public void shouldUpdateReceived2Initializing2Initialized() {
        Task task = getStubTask();
        task.changeStatus(RECEIVED);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(true);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(true);

        when(taskRepository.save(task)).thenReturn(task);
        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(CHAIN_TASK_ID));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(ChainTask.builder()
                .contributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60).getTime())
                .build()));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getChainDealId()).isEqualTo(CHAIN_DEAL_ID);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 1).getStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(INITIALIZING);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus()).isEqualTo(RECEIVED);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    // Tests on initializing2Initialized transition

    @Test
    public void shouldUpdateInitializing2Initialized() {
        Task task = getStubTask();
        task.setChainTaskId(CHAIN_TASK_ID);
        task.changeStatus(INITIALIZING);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 1).getStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(INITIALIZING);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    public void shouldNotUpdateInitializing2InitializedSinceNotInitialized() {
        Task task = getStubTask();
        task.setChainTaskId(CHAIN_TASK_ID);
        task.changeStatus(INITIALIZING);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.of(false));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 4).getStatus()).isEqualTo(RECEIVED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus()).isEqualTo(INITIALIZING);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(INITIALIZE_FAILED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 1).getStatus()).isEqualTo(FAILED);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    public void shouldNotUpdateInitializing2InitializedSinceFailedToCheck() {
        Task task = getStubTask();
        task.setChainTaskId(CHAIN_TASK_ID);
        task.changeStatus(INITIALIZING);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(RECEIVED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 1).getStatus()).isEqualTo(INITIALIZING);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZING);
    }

    // Tests on initialized2Running transition

    @Test
    public void shouldUpdateInitialized2Running() { // 1 RUNNING out of 2
        Task task = getStubTask();
        task.changeStatus(INITIALIZED);

        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.STARTING, ReplicateStatus.COMPUTED)).thenReturn(2);
        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.COMPUTED)).thenReturn(0);
        when(taskRepository.save(task)).thenReturn(task);
        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    public void shouldNotUpdateInitialized2RunningSinceNoRunningOrComputedReplicates() {
        Task task = getStubTask();
        task.changeStatus(INITIALIZED);

        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.STARTING, ReplicateStatus.COMPUTED)).thenReturn(0);
        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.COMPUTED)).thenReturn(0);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(RUNNING);
    }

    @Test
    public void shouldNotUpdateInitialized2RunningSinceComputedIsMoreThanNeeded() {
        Task task = getStubTask();
        task.changeStatus(INITIALIZED);

        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.STARTING, ReplicateStatus.COMPUTED)).thenReturn(2);
        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.COMPUTED)).thenReturn(4);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    // initializedOrRunning2ContributionTimeout

    @Test
    public void shouldNotUpdateInitializedOrRunning2ContributionTimeoutSinceBeforeTimeout() {
        Date now = new Date();
        Date timeoutInFuture = DateTimeUtils.addMinutesToDate(now, 1);

        Task task = getStubTask();
        task.changeStatus(INITIALIZED);
        task.setContributionDeadline(timeoutInFuture);

        ChainTask chainTask = ChainTask.builder()
                .contributionDeadline(timeoutInFuture.getTime())
                .status(ChainTaskStatus.ACTIVE)
                .build();

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(chainTask));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(CONTRIBUTION_TIMEOUT);
    }

    @Test
    public void shouldNotUpdateInitializedOrRunning2ContributionTimeoutSinceChainTaskIsntActive() {
        Date now = new Date();
        Date timeoutInPast = DateTimeUtils.addMinutesToDate(now, -1);

        Task task = getStubTask();
        task.changeStatus(INITIALIZED);
        task.setContributionDeadline(timeoutInPast);

        ChainTask chainTask = ChainTask.builder()
                .contributionDeadline(timeoutInPast.getTime())
                .status(ChainTaskStatus.REVEALING)
                .build();

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(chainTask));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(CONTRIBUTION_TIMEOUT);
    }

    @Test
    public void shouldNotReSendNotificationWhenAlreadyInContributionTimeout() {
        Date now = new Date();
        Date timeoutInPast = DateTimeUtils.addMinutesToDate(now, -1);

        Task task = getStubTask();
        task.changeStatus(CONTRIBUTION_TIMEOUT);
        task.setContributionDeadline(timeoutInPast);

        ChainTask chainTask = ChainTask.builder()
                .contributionDeadline(timeoutInPast.getTime())
                .status(ChainTaskStatus.ACTIVE)
                .build();

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(chainTask));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        Mockito.verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }


    @Test
    public void shouldUpdateFromInitializedOrRunning2ContributionTimeout() {
        Date now = new Date();
        Date timeoutInPast = DateTimeUtils.addMinutesToDate(now, -1);

        Task task = getStubTask();
        task.changeStatus(INITIALIZED);
        task.setContributionDeadline(timeoutInPast);

        ChainTask chainTask = ChainTask.builder()
                .contributionDeadline(timeoutInPast.getTime())
                .status(ChainTaskStatus.ACTIVE)
                .build();

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);

        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
        assertThat(task.getLastButOneStatus()).isEqualTo(CONTRIBUTION_TIMEOUT);
    }


    // Tests on running2ConsensusReached transition

    @Test
    public void shouldUpdateRunning2ConsensusReached() {
        Task task = getStubTask();
        task.changeStatus(RUNNING);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build()));
        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbValidContributedWinners(any(), any())).thenReturn(2);
        when(taskRepository.save(task)).thenReturn(task);
        when(web3jService.getLatestBlockNumber()).thenReturn(2L);
        when(iexecHubService.getConsensusBlock(anyString(), anyLong())).thenReturn(ChainReceipt.builder().blockNumber(1L).build());
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    @Test
    public void shouldNotUpdateRunning2ConsensusReachedSinceWrongTaskStatus() {
        Task task = getStubTask();
        task.changeStatus(INITIALIZED);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build()));
        when(replicatesService.getNbOffChainReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.CONTRIBUTED)).thenReturn(2);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    public void shouldNotUpdateRunning2ConsensusReachedSinceCannotGetChainTask() {
        Task task = getStubTask();
        task.changeStatus(RUNNING);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.empty());

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    public void shouldNOTUpdateRunning2ConsensusReachedSinceOnChainStatusNotRevealing() {
        Task task = getStubTask();
        task.changeStatus(RUNNING);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.UNSET)
                .winnerCounter(2)
                .build()));
        when(replicatesService.getNbOffChainReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.CONTRIBUTED)).thenReturn(2);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    public void shouldNOTUpdateRunning2ConsensusReachedSinceWinnerContributorsDiffers() {
        Task task = getStubTask();
        task.changeStatus(RUNNING);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build()));
        when(replicatesService.getNbOffChainReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.CONTRIBUTED)).thenReturn(1);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    // Tests on consensusReached2AtLeastOneReveal2UploadRequested transition

    @Test
    public void shouldUpdateConsensusReached2AtLeastOneReveal2UploadRequested() {
        Task task = getStubTask();
        task.changeStatus(CONSENSUS_REACHED);
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.REVEALED)).thenReturn(1);
        when(taskRepository.save(task)).thenReturn(task);
        when(replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId())).thenReturn(Optional.of(replicate));
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getUploadingWorkerWalletAddress()).isEqualTo(replicate.getWalletAddress());
        int size = task.getDateStatusList().size();
        assertThat(task.getDateStatusList().get(size - 2).getStatus()).isEqualTo(AT_LEAST_ONE_REVEALED);
        assertThat(task.getDateStatusList().get(size - 1).getStatus()).isEqualTo(RESULT_UPLOAD_REQUESTED);
    }

    @Test
    public void shouldNOTUpdateConsensusReached2AtLeastOneRevealSinceNoRevealedReplicate() {
        Task task = getStubTask();
        task.changeStatus(CONSENSUS_REACHED);
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.REVEALED)).thenReturn(0);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    // Tests on uploadRequested2UploadingResult transition
    @Test
    public void shouldUpdateFromUploadRequestedToUploadingResult() {
        Task task = getStubTask();
        task.setCurrentStatus(RESULT_UPLOAD_REQUESTED);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOADING)).thenReturn(1);

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RESULT_UPLOADING);
    }

    @Test
    public void shouldNotUpdateFromUploadRequestedToUploadingResultSinceNoWorkerUploading() {
        Task task = getStubTask();
        task.changeStatus(RESULT_UPLOAD_REQUESTED);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOADING)).thenReturn(0);

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RESULT_UPLOAD_REQUESTED);

        // check that the request upload method has been called
        Mockito.verify(replicatesService, Mockito.times(1))
           .getRandomReplicateWithRevealStatus(any());

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RESULT_UPLOAD_REQUESTED);
    }

    // Test on resultUploading2Uploaded2Finalizing2Finalized

    @Test
    public void shouldUpdateResultUploading2Uploaded2Finalizing2Finalized() { //one worker uploaded
        Task task = getStubTask();
        task.changeStatus(RESULT_UPLOADING);

        ChainTask chainTask = ChainTask.builder().revealCounter(1).build();
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOADED)).thenReturn(1);
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(1);
        when(iexecHubService.canFinalize(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.getChainTask(any())).thenReturn(Optional.of(chainTask));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(blockchainAdapterService.requestFinalize(any(), any(), any())).thenReturn(Optional.of(CHAIN_TASK_ID));
        when(blockchainAdapterService.isFinalized(any())).thenReturn(Optional.of(true));
        when(resulRepositoryConfig.getResultRepositoryURL()).thenReturn("http://foo:bar");
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.COMPLETED)
                .revealCounter(1)
                .build()));

        taskService.updateTaskRunnable(task.getChainTaskId());

        TaskStatus lastButOneStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus();
        TaskStatus lastButTwoStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus();
        TaskStatus lastButThreeStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 4).getStatus();

        assertThat(task.getCurrentStatus()).isEqualTo(COMPLETED);
        assertThat(lastButOneStatus).isEqualTo(FINALIZED);
        assertThat(lastButTwoStatus).isEqualTo(FINALIZING);
        assertThat(lastButThreeStatus).isEqualTo(RESULT_UPLOADED);
    }

    // Tests on finalizing2Finalized transition

    @Test
    public void shouldUpdateFinalizing2Finalized2Completed() {
        Task task = getStubTask();
        task.setChainTaskId(CHAIN_TASK_ID);
        task.changeStatus(FINALIZING);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(blockchainAdapterService.isFinalized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(COMPLETED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(FINALIZED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus()).isEqualTo(FINALIZING);
    }

    @Test
    public void shouldUpdateFinalizing2FinalizedFailed() {
        Task task = getStubTask();
        task.setChainTaskId(CHAIN_TASK_ID);
        task.changeStatus(FINALIZING);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(blockchainAdapterService.isFinalized(CHAIN_TASK_ID)).thenReturn(Optional.of(false));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(FINALIZE_FAILED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus()).isEqualTo(FINALIZING);
    }

    @Test
    public void shouldUpdateResultUploading2UploadedButNot2Finalizing() { //one worker uploaded
        Task task = getStubTask();
        task.changeStatus(RESULT_UPLOADING);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));
        when(iexecHubService.canFinalize(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(false);

        taskService.updateTaskRunnable(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(FINALIZING);
    }

    @Test
    public void shouldUpdateResultUploading2Uploaded2Finalizing2FinalizeFail() { //one worker uploaded && finalize FAIL
        Task task = getStubTask();
        task.changeStatus(RESULT_UPLOADING);
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOADED)).thenReturn(1);
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(1);
        when(iexecHubService.canFinalize(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(blockchainAdapterService.requestFinalize(any(), any(), any())).thenReturn(Optional.empty());
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.FAILLED)
                .revealCounter(1)
                .build()));

        taskService.updateTaskRunnable(task.getChainTaskId());

        TaskStatus lastButOneStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus();
        TaskStatus lastButTwoStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus();

        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
        assertThat(lastButOneStatus).isEqualTo(FINALIZE_FAILED);
        assertThat(lastButTwoStatus).isEqualTo(RESULT_UPLOADED);
    }


    @Test
    public void shouldUpdateResultUploading2UploadedFailAndRequestUploadAgain() {
        Task task = getStubTask();
        task.changeStatus(RESULT_UPLOADING);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOADED)).thenReturn(0);
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOAD_REQUEST_FAILED)).thenReturn(1);
        when(replicatesService.getNbOffChainReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.REVEALED)).thenReturn(1);
        when(replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId())).thenReturn(Optional.of(replicate));
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getUploadingWorkerWalletAddress()).isEqualTo(replicate.getWalletAddress());
        int size = task.getDateStatusList().size();
        assertThat(task.getDateStatusList().get(size - 2).getStatus()).isEqualTo(RESULT_UPLOADING);
        assertThat(task.getDateStatusList().get(size - 1).getStatus()).isEqualTo(RESULT_UPLOAD_REQUESTED);
    }


    @Test
    public void shouldWaitUpdateReplicateStatusFromUnsetToContributed() {
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("0x1", "chainTaskId"));

        replicates.get(0).updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.RUNNING));

        Task task = Task.builder()
                .id("taskId")
                .chainTaskId("chainTaskId")
                .currentStatus(TaskStatus.RUNNING)
                .commandLine("ls")
                .dateStatusList(dateStatusList)
                .build();

        when(taskRepository.findByChainTaskId("chainTaskId")).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);
        ChainContribution chainContribution = ChainContribution.builder().status(ChainContributionStatus.UNSET).build();
        when(iexecHubService.getChainContribution("chainTaskId", "0x1")).thenReturn(Optional.of(chainContribution));


        Runnable runnable1 = () -> {
            // taskService.updateReplicateStatus("chainTaskId", "0x1", ReplicateStatus.CONTRIBUTED);
            // Optional<Replicate> replicate = task.getReplicate("0x1");
            // assertThat(replicate.isPresent()).isTrue();
            // assertThat(replicate.get().getCurrentStatus()).isEqualTo(ReplicateStatus.COMPUTED);
        };

        Thread thread1 = new Thread(runnable1);
        thread1.start();

        Runnable runnable2 = () -> {
            sleep(500L);
            chainContribution.setStatus(ChainContributionStatus.CONTRIBUTED);
            sleep(500L);
            // assertThat(task.getReplicate("0x1").get().getCurrentStatus()).isEqualTo(ReplicateStatus.CONTRIBUTED);
        };

        Thread thread2 = new Thread(runnable2);
        thread2.start();
    }


    // 3 replicates in RUNNING 0 in COMPUTED
    @Test
    public void shouldUpdateTaskToRunningFromWorkersInRunning() {
        Task task = getStubTask();
        task.changeStatus(INITIALIZED);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.STARTING, ReplicateStatus.COMPUTED)).thenReturn(3);
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(0);

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    // 2 replicates in RUNNING and and 2 in COMPUTED
    @Test
    public void shouldUpdateTaskToRunningFromWorkersInRunningAndComputed() {
        Task task = getStubTask();
        task.changeStatus(INITIALIZED);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.STARTING, ReplicateStatus.COMPUTED)).thenReturn(4);
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(2);

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    // all replicates in INITIALIZED
    @Test
    public void shouldNotUpdateToRunningSinceAllReplicatesInCreated() {
        Task task = getStubTask();
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.STARTING, ReplicateStatus.COMPUTED)).thenReturn(0);
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(0);

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RUNNING);
    }

    // Two replicates in COMPUTED BUT numWorkersNeeded = 2, so the task should not be able to move directly from
    // INITIALIZED to COMPUTED
    @Test
    public void shouldNotUpdateToRunningCase2() {
        Task task = getStubTask();
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.STARTING, ReplicateStatus.COMPUTED)).thenReturn(2);
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(2);

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RUNNING);
    }


    // at least one UPLOADED
    @Test
    public void shouldUpdateFromUploadingResultToResultUploaded() {
        Task task = getStubTask();
        task.changeStatus(TaskStatus.RUNNING);
        task.changeStatus(TaskStatus.RESULT_UPLOAD_REQUESTED);
        task.changeStatus(TaskStatus.RESULT_UPLOADING);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RESULT_UPLOADED);
        assertThat(task.getResultLink()).isEqualTo(RESULT_LINK);
    }

    // No worker in UPLOADED
    @Test
    public void shouldNotUpdateToResultUploaded() {
        Task task = getStubTask();
        task.changeStatus(TaskStatus.RUNNING);
        task.changeStatus(TaskStatus.RESULT_UPLOAD_REQUESTED);
        task.changeStatus(TaskStatus.RESULT_UPLOADING);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOADED)).thenReturn(0);

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RESULT_UPLOADED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    public void shouldNotUpdateFromResultUploadedToFinalizingSinceNotEnoughGas() {
        Task task = getStubTask();
        task.changeStatus(RUNNING);
        task.changeStatus(RESULT_UPLOAD_REQUESTED);
        task.changeStatus(RESULT_UPLOADING);
        task.changeStatus(RESULT_UPLOADED);
        ChainTask chainTask = ChainTask.builder().revealCounter(1).build();

        when(iexecHubService.canFinalize(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(chainTask));
        when(replicatesService.getNbReplicatesContainingStatus(task.getChainTaskId(), ReplicateStatus.REVEALED)).thenReturn(1);
        when(iexecHubService.hasEnoughGas()).thenReturn(false);

        taskService.updateTaskRunnable(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADED);
    }


    @Test
    public void shouldUpdateFromAnyInProgressStatus2FinalDeadlineReached() {
        Task task = getStubTask();
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        task.changeStatus(RECEIVED);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(FINAL_DEADLINE_REACHED);
    }

    @Test
    public void shouldUpdateFromFinalDeadlineReached2Failed() {
        Task task = getStubTask();
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        task.changeStatus(FINAL_DEADLINE_REACHED);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    public void shouldNotUpdateToFinalDeadlineReachedIfAlreadyFailed() {
        Task task = getStubTask();
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        task.changeStatus(FAILED);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    public void shouldNotUpdateToFinalDeadlineReachedIfAlreadyCompleted() {
        Task task = getStubTask();
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        task.changeStatus(COMPLETED);

        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

        taskService.updateTaskRunnable(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(COMPLETED);
    }

}
