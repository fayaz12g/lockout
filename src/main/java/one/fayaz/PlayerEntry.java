package one.fayaz;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerEntry {
    private final UUID uuid;
    private final String name;
    private final int color;
    private final List<String> claims; // Generic: deaths or kills

    public PlayerEntry(UUID uuid, String name, int color) {
        this.uuid = uuid;
        this.name = name;
        this.color = color;
        this.claims = new ArrayList<>();
    }

    public PlayerEntry(UUID uuid, String name, int color, List<String> claims) {
        this.uuid = uuid;
        this.name = name;
        this.color = color;
        this.claims = new ArrayList<>(claims);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public int getColor() {
        return color;
    }

    public List<String> getClaims() {
        return claims;
    }

    public void addClaim(String claim) {
        claims.add(claim);
    }

    public int getScore() {
        return claims.size();
    }

    // Backwards compatibility
    @Deprecated
    public List<String> getDeaths() {
        return getClaims();
    }

    @Deprecated
    public void addDeath(String death) {
        addClaim(death);
    }
}