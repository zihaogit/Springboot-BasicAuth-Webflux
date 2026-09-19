package com.example.springbootbasiclogin.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.ZonedDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "processed_webhook_events")
public class ProcessedWebhookEvent implements Persistable<String> {

    @Id
    @Column("event_id")
    private String eventId;

    @Column("event_type")
    private String eventType;

    @Column("processed_at")
    private ZonedDateTime processedAt;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public String getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }
}
