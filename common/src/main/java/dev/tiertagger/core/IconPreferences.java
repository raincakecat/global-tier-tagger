package dev.tiertagger.core;
import java.util.*;
/** Global icon choices are shared by mode; a slot override takes precedence. */
public final class IconPreferences {
 private IconPreferences() {}
 public static String selected(String mode, Map<String,String> global, Map<String,String> slotOverrides) {
  Objects.requireNonNull(mode);Objects.requireNonNull(global);Objects.requireNonNull(slotOverrides);
  String slot=slotOverrides.get(mode);if(slot!=null && !slot.isBlank())return slot;
  String chosen=global.get(mode);return chosen==null || chosen.isBlank()?"default":chosen;
 }
}
