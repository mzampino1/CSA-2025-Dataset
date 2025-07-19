java
public void setUuid(String uuid) {
    if (uuid == null || uuid.isEmpty()) {
        throw new IllegalArgumentException("UUID cannot be null or empty");
    }
    this.uuid = uuid;
}