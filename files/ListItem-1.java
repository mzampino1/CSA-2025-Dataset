package eu.siacs.conversations.entities;

public interface ListItem extends Comparable<ListItem> {
    public String getDisplayName();

    public String getJid();
}

class User implements ListItem {
    private String displayName;
    private String jid;

    public User(String displayName, String jid) {
        this.displayName = displayName;
        this.jid = jid;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getJid() {
        return jid;
    }

    // Vulnerability: Comparing object references instead of their contents.
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;  // This is fine for reference equality

        if (obj instanceof User) {
            User other = (User) obj;
            // Incorrectly comparing references rather than values
            return this.jid == other.jid; // Vulnerability here: should use .equals() instead of ==
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((displayName == null) ? 0 : displayName.hashCode());
        result = prime * result + ((jid == null) ? 0 : jid.hashCode());
        return result;
    }

    @Override
    public int compareTo(ListItem other) {
        if (other instanceof User) {
            // Proper comparison of string values for ordering
            return this.getDisplayName().compareTo(((User) other).getDisplayName());
        }
        throw new IllegalArgumentException("Cannot compare to non-User object");
    }
}