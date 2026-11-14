package dev.tiertagger.core;

import java.util.*;

/** Viewer preferences and resolution of rankings supplied by an external source. */
public final class TagSelection {
    private TagSelection() {}
    public record Slot(String provider, String modeOverride) {
        public Slot {
            if (provider == null || provider.isBlank()) throw new IllegalArgumentException("Provider is required");
            if (modeOverride != null && modeOverride.isBlank()) modeOverride = null;
        }
        public String mode(String sharedMode) { return modeOverride == null ? sharedMode : modeOverride; }
    }
    public record Settings(String sharedMode, Slot primary, Slot secondary, Slot tertiary) {
        public Settings {
            if (sharedMode == null || sharedMode.isBlank()) throw new IllegalArgumentException("Game mode is required");
            Objects.requireNonNull(primary, "Primary slot is required");
        }
        public static Settings defaults() { return new Settings("crystal", new Slot("mctiers", null), null, null); }
    }
    public record Tag(String provider, String mode, String tier) {}
    @FunctionalInterface public interface Rankings { Optional<String> find(String provider, String mode); }
    private static Optional<Tag> resolve(Slot slot, String sharedMode, Rankings rankings) {
        if (slot == null) return Optional.empty();
        String mode = slot.mode(sharedMode);
        return rankings.find(slot.provider(), mode).filter(tier -> !tier.isBlank())
            .map(tier -> new Tag(slot.provider(), mode, tier));
    }
    public static List<Tag> nameTag(Settings settings, Rankings rankings) {
        Objects.requireNonNull(settings); Objects.requireNonNull(rankings);
        List<Tag> tags = new ArrayList<>(3);
        for (Slot slot : new Slot[]{settings.primary(), settings.secondary(), settings.tertiary()})
            resolve(slot, settings.sharedMode(), rankings).ifPresent(tags::add);
        return List.copyOf(tags);
    }
    public static Optional<Tag> tabTag(Settings settings, Rankings rankings) {
        Objects.requireNonNull(settings); Objects.requireNonNull(rankings);
        return resolve(settings.primary(), settings.sharedMode(), rankings);
    }
}
