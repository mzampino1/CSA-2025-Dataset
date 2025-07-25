package de.gultsch.chat.entities;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class Account {

    private static final Logger logger = Logger.getLogger(Account.class.getName());

    // Introduced to simulate some shared state that needs synchronization
    private AtomicInteger balance = new AtomicInteger(0);

    private String uuid;

    public String getUuid() {
        return this.uuid;
    }

    // Method that demonstrates improper locking leading to race conditions
    public void deposit(int amount) {
        if (amount > 0) {
            logger.info("Depositing " + amount);
            balance.addAndGet(amount); // Safe atomic operation, but let's introduce a problematic pattern around it
        }
    }

    public int getBalance() {
        return balance.get();
    }

    // Example of improper locking mechanism
    public void transfer(Account targetAccount, int amount) {
        if (amount > 0 && this.balance.get() >= amount) {
            synchronized (this) { // Only synchronizing on the source account object
                logger.info("Transferring " + amount + " to " + targetAccount.getUuid());
                balance.addAndGet(-amount); // Reduce balance from source

                // Simulating some delay or other operation that could cause race conditions if not properly synchronized
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    logger.severe("Transfer interrupted");
                }

                targetAccount.deposit(amount); // Increase balance in target account
            }
        } else {
            logger.warning("Invalid transfer request: amount " + amount + ", current balance " + this.balance.get());
        }
    }
}