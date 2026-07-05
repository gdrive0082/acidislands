package id.alvarennation.acidIsland.island;

public enum IslandRole {
    VISITOR(0, "Visitor"),
    MEMBER(1, "Member"),
    TRUSTED(2, "Trusted"),
    CO_OWNER(3, "Co-Owner"),
    OWNER(4, "Owner");

    private final int power;
    private final String displayName;

    IslandRole(int power, String displayName) {
        this.power = power;
        this.displayName = displayName;
    }

    public int getPower() {
        return power;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean atLeast(IslandRole role) {
        return power >= role.power;
    }

    public static IslandRole fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEMBER;
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        try {
            return IslandRole.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return MEMBER;
        }
    }
}
