package com.example.springbootbasiclogin.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@Getter
@Setter
@Table("verification_otp")
public class VerificationOTP implements Persistable<UUID> {

    @Id
    @Column("otp_id")
    private UUID id;

    @Column("user_id")
    private int userId; // Reference to the User entity

    @Column("otp")
    private int otp;

    @Column("token")
    private String token;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    //TODO: Change to Instant if LocalDateTime cannot work
    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    /**
     * Transient flag — true for newly constructed entities,
     * false for entities loaded from the database via the @PersistenceCreator constructor.
     */
    @Transient
    private boolean newRecord = true;

    /**
     * Constructor used by Spring Data when loading from the database.
     * Sets newRecord = false so save() triggers an UPDATE.
     */
    @PersistenceCreator
    public VerificationOTP(UUID id, int userId, int otp, String token,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.otp = otp;
        this.token = token;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.newRecord = false;
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }
}