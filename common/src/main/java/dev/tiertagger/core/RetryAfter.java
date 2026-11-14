package dev.tiertagger.core;
import java.time.*;import java.time.format.DateTimeFormatter;
/** Retry-After accepts both delta seconds and the HTTP-date wire format. */
public final class RetryAfter {
 private RetryAfter(){}
 public static long seconds(String value,long fallback){try{return Math.clamp(Long.parseLong(value),1,86400);}catch(RuntimeException ignored){}try{return Math.clamp(Duration.between(Instant.now(),ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds()+1,1,86400);}catch(RuntimeException ignored){return fallback;}}
}
