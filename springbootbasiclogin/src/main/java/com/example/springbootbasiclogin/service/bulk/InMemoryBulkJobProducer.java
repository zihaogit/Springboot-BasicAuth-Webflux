package com.example.springbootbasiclogin.service.bulk;

import com.example.springbootbasiclogin.dto.bulk.BulkJobMessage;
import com.example.springbootbasiclogin.dto.bulk.BulkRegisterRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@Profile("!kafka")
@RequiredArgsConstructor
public class InMemoryBulkJobProducer implements BulkJobProducer {

    private final BulkJobProcessor bulkJobProcessor;
    private final Sinks.Many<BulkJobMessage> sink = Sinks.many().unicast().onBackpressureBuffer();

    @PostConstruct
    public void startWorker() {
        sink.asFlux()
                .publishOn(Schedulers.boundedElastic())
                .concatMap(message -> {
                    log.info("In-memory worker received bulk job: {}", message.getJobId());
                    return bulkJobProcessor.processJob(message.getJobId(), message.getRequest())
                            .onErrorResume(e -> {
                                log.error("Error processing in-memory bulk job {}", message.getJobId(), e);
                                return Mono.empty();
                            });
                })
                .subscribe();
    }

    @Override
    public Mono<Void> sendJob(String jobId, BulkRegisterRequest request, String submittedBy) {
        return Mono.fromRunnable(() -> {
            BulkJobMessage message = new BulkJobMessage(jobId, request, submittedBy);
            Sinks.EmitResult result = sink.tryEmitNext(message);
            if (result.isFailure()) {
                log.error("Failed to enqueue bulk job {} to in-memory queue: {}", jobId, result);
                throw new IllegalStateException("Failed to enqueue job: " + result);
            }
            log.info("Enqueued bulk job {} to in-memory queue", jobId);
        });
    }
}
