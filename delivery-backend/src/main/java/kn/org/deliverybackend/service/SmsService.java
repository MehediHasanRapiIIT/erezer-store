package kn.org.deliverybackend.service;

public interface SmsService {

    /**
     * Best-effort send of a transactional SMS. Implementations must never throw
     * into the caller — SMS is a side-channel and must not break order flows.
     *
     * @param toPhone E.164 or local BD number; ignored if blank
     * @param message the text body
     */
    void send(String toPhone, String message);
}
