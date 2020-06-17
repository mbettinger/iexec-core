package com.iexec.core.task.stdout;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface TaskStdoutRepository extends MongoRepository<TaskStdout, String> {

    Optional<TaskStdout> findOneByChainTaskId(String chainTaskId);

    @Query(value = "{ chainTaskId: ?0 }", fields = "{ replicateStdoutList: { $elemMatch: { walletAddress: ?1 } } }")
    Optional<TaskStdout> findByChainTaskIdAndWalletAddress(String chainTaskId, String walletAddress);
}
