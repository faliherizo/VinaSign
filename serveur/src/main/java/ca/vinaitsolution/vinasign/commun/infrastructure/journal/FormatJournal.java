package ca.vinaitsolution.vinasign.commun.infrastructure.journal;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

/**
 * Journal structuré en JSON, une ligne par événement (VS-004), relié aux traces par {@code trace.id} et
 * {@code span.id} (posés par Micrometer Tracing). Le message et le message d'erreur passent par
 * {@link MasqueRenseignements} ; la pile d'appels est réduite au type de l'exception : elle ne transporte jamais les
 * valeurs manipulées.
 */
public class FormatJournal implements StructuredLogFormatter<ILoggingEvent> {

    private final JsonWriter<ILoggingEvent> ecrivain = JsonWriter.<ILoggingEvent>of(m -> {
        m.add("@timestamp", e -> Instant.ofEpochMilli(e.getTimeStamp()).toString());
        m.add("log.level", e -> e.getLevel().toString());
        m.add("log.logger", ILoggingEvent::getLoggerName);
        m.add("process.thread.name", ILoggingEvent::getThreadName);
        m.add("service.name", e -> "vinasign");
        m.add("trace.id", e -> mdc(e, "traceId")).whenNotNull();
        m.add("span.id", e -> mdc(e, "spanId")).whenNotNull();
        m.add("message", e -> MasqueRenseignements.appliquer(e.getFormattedMessage()));
        m.add("error.type", e -> erreur(e) == null ? null : erreur(e).getClassName()).whenNotNull();
        m.add("error.message", e -> erreur(e) == null ? null : MasqueRenseignements.appliquer(erreur(e).getMessage())).whenNotNull();
    }).withNewLineAtEnd();

    @Override
    public String format(ILoggingEvent evenement) {
        return ecrivain.writeToString(evenement);
    }

    private static String mdc(ILoggingEvent e, String cle) {
        Map<String, String> mdc = e.getMDCPropertyMap();
        return mdc == null ? null : mdc.get(cle);
    }

    private static IThrowableProxy erreur(ILoggingEvent e) {
        return e.getThrowableProxy();
    }
}
