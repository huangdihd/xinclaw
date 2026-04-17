package xin.agent.tasks;

import java.util.UUID;

public class Task {
    public enum Status { TODO, IN_PROGRESS, DONE }

    private final String id;
    private String description;
    private Status status;
    private long createdAt;

    public Task(String description) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.description = description;
        this.status = Status.TODO;
        this.createdAt = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getId() { return id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public long getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return String.format("[%s] ID:%s - %s", status, id, description);
    }
}
