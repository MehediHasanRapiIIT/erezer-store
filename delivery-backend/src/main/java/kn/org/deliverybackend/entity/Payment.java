package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Payment extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    private String method;

    private String transactionId;

    private BigDecimal amount;

    private String status;

    // ── Phase 4: gateway integration columns ──────────────────────────────────
    @Column(length = 32)
    private String provider;

    @Column(name = "provider_payment_id", length = 128)
    private String providerPaymentId;

    @Column(name = "provider_trx_id", length = 128)
    private String providerTrxId;

    @Column(name = "payer_account", length = 64)
    private String payerAccount;

    @Column(name = "callback_url", length = 1024)
    private String callbackUrl;

    @Column(columnDefinition = "TEXT")
    private String metadata;
}
