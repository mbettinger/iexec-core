/*
 * Copyright 2021 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.chain.adapter;

import com.iexec.common.chain.adapter.CommandStatus;
import com.iexec.common.chain.adapter.args.TaskFinalizeArgs;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.mockito.Mockito.when;

public class BlockchainAdapterServiceTests {

    public static final String CHAIN_TASK_ID = "CHAIN_TASK_ID";
    public static final String CHAIN_DEAL_ID = "CHAIN_DEAL_ID";
    public static final int TASK_INDEX = 0;
    public static final String LINK = "link";
    public static final String CALLBACK = "callback";
    public static final int PERIOD = 10;
    public static final int MAX_ATTEMPTS = 3;
    public static final int ATTEMPT = 0;

    @Mock
    private BlockchainAdapterClient blockchainAdapterClient;

    @InjectMocks
    private BlockchainAdapterService blockchainAdapterService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    //Initialize

    @Test
    public void requestInitialize() {
        when(blockchainAdapterClient.requestInitializeTask(CHAIN_DEAL_ID, TASK_INDEX))
                .thenReturn(ResponseEntity.ok(CHAIN_TASK_ID));

        Assertions.assertThat(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, TASK_INDEX))
                .isPresent();
    }

    @Test
    public void requestInitializeFailedSinceNot200() {
        when(blockchainAdapterClient.requestInitializeTask(CHAIN_DEAL_ID, TASK_INDEX))
                .thenReturn(ResponseEntity.badRequest().build());

        Assertions.assertThat(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, TASK_INDEX))
                .isEmpty();
    }

    @Test
    public void requestInitializeFailedSinceNoBody() {
        when(blockchainAdapterClient.requestInitializeTask(CHAIN_DEAL_ID, TASK_INDEX))
                .thenReturn(ResponseEntity.ok().build());

        Assertions.assertThat(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, TASK_INDEX))
                .isEmpty();
    }

    @Test
    public void isInitialized() {
        when(blockchainAdapterClient.getStatusForInitializeTaskRequest(CHAIN_TASK_ID))
                .thenReturn(ResponseEntity.ok(CommandStatus.SUCCESS));
        Assertions.assertThat(blockchainAdapterService.isInitialized(CHAIN_TASK_ID))
                .isEqualTo(Optional.of(true));
    }

    // Finalize

    @Test
    public void requestFinalize() {
        when(blockchainAdapterClient.requestFinalizeTask(CHAIN_TASK_ID, new TaskFinalizeArgs(LINK, CALLBACK)))
                .thenReturn(ResponseEntity.ok(CHAIN_TASK_ID));

        Assertions.assertThat(blockchainAdapterService.requestFinalize(CHAIN_TASK_ID, LINK, CALLBACK))
                .isPresent();
    }

    @Test
    public void requestFinalizeFailedSinceNot200() {
        when(blockchainAdapterClient.requestFinalizeTask(CHAIN_TASK_ID, new TaskFinalizeArgs(LINK, CALLBACK)))
                .thenReturn(ResponseEntity.ok().build());

        Assertions.assertThat(blockchainAdapterService.requestFinalize(CHAIN_TASK_ID, LINK, CALLBACK))
                .isEmpty();
    }

    @Test
    public void requestFinalizeFailedSinceNoBody() {
        when(blockchainAdapterClient.requestFinalizeTask(CHAIN_TASK_ID, new TaskFinalizeArgs(LINK, CALLBACK)))
                .thenReturn(ResponseEntity.badRequest().build());

        Assertions.assertThat(blockchainAdapterService.requestFinalize(CHAIN_TASK_ID, LINK, CALLBACK))
                .isEmpty();
    }

    @Test
    public void isFinalized() {
        when(blockchainAdapterClient.getStatusForFinalizeTaskRequest(CHAIN_TASK_ID))
                .thenReturn(ResponseEntity.ok(CommandStatus.SUCCESS));
        Assertions.assertThat(blockchainAdapterService.isFinalized(CHAIN_TASK_ID))
                .isEqualTo(Optional.of(true));
    }

    // Testing ability to pull & wait

    @Test
    public void isCommandCompletedWithSuccess() {
        when(blockchainAdapterClient.getStatusForInitializeTaskRequest(CHAIN_TASK_ID))
                .thenReturn(ResponseEntity.ok(CommandStatus.RECEIVED))
                .thenReturn(ResponseEntity.ok(CommandStatus.PROCESSING))
                .thenReturn(ResponseEntity.ok(CommandStatus.SUCCESS));

        Optional<Boolean> commandCompleted = blockchainAdapterService
                .isCommandCompleted(blockchainAdapterClient::getStatusForInitializeTaskRequest,
                CHAIN_TASK_ID, PERIOD, MAX_ATTEMPTS, ATTEMPT);
        Assertions.assertThat(commandCompleted.isPresent()).isTrue();
        Assertions.assertThat(commandCompleted.get()).isTrue();
    }

    @Test
    public void isCommandCompletedWithFailure() {
        when(blockchainAdapterClient.getStatusForInitializeTaskRequest(CHAIN_TASK_ID))
                .thenReturn(ResponseEntity.ok(CommandStatus.RECEIVED))
                .thenReturn(ResponseEntity.ok(CommandStatus.PROCESSING))
                .thenReturn(ResponseEntity.ok(CommandStatus.FAILURE));

        Optional<Boolean> commandCompleted = blockchainAdapterService
                .isCommandCompleted(blockchainAdapterClient::getStatusForInitializeTaskRequest,
                CHAIN_TASK_ID, PERIOD, MAX_ATTEMPTS, ATTEMPT);
        Assertions.assertThat(commandCompleted.isPresent()).isTrue();
        Assertions.assertThat(commandCompleted.get()).isFalse();
    }


}