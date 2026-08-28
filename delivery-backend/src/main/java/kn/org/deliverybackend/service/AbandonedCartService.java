package kn.org.deliverybackend.service;

public interface AbandonedCartService {

    /**
     * Run the daily scan immediately (also wired to {@code @Scheduled} cron).
     * Returns the number of customers actually emailed.
     */
    int sweepOnce();
}
