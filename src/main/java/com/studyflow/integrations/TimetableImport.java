package com.studyflow.integrations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads a bounded iCalendar timetable without making network requests or changing its source file. */
public final class TimetableImport {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_COMPONENTS = 2000;
    private static final int MAX_OCCURRENCES = 10_000;
    private static final int MAX_SERIES_DAYS = 3660;
    private static final int MAX_EVENT_DAYS = 366;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern DURATION = Pattern.compile(
            "\\+?P(?:(\\d+)W|(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?)");
    private static final Map<String, DayOfWeek> WEEKDAYS = Map.of(
            "MO", DayOfWeek.MONDAY, "TU", DayOfWeek.TUESDAY, "WE", DayOfWeek.WEDNESDAY,
            "TH", DayOfWeek.THURSDAY, "FR", DayOfWeek.FRIDAY, "SA", DayOfWeek.SATURDAY, "SU", DayOfWeek.SUNDAY);

    private TimetableImport() { }

    public static List<Event> read(Path source) throws IOException {
        return read(source, ZoneId.systemDefault());
    }

    public static List<Event> read(Path source, ZoneId displayZone) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(displayZone, "displayZone");
        try {
            List<Component> components = components(readText(source));
            Map<String, List<Component>> series = new LinkedHashMap<>();
            for (Component component : components) {
                String uid = component.text("UID");
                if (uid.isBlank()) throw invalid("Every timetable event needs a UID.");
                series.computeIfAbsent(uid, ignored -> new ArrayList<>()).add(component);
            }
            List<Event> events = new ArrayList<>();
            for (var entry : series.entrySet()) {
                expand(entry.getKey(), entry.getValue(), displayZone, events);
                if (events.size() > MAX_OCCURRENCES) throw invalid("This timetable expands to too many events.");
            }
            return events.stream().sorted(Comparator.comparing(Event::start).thenComparing(Event::end)
                    .thenComparing(Event::title).thenComparing(Event::id)).toList();
        } catch (DateTimeException | ArithmeticException | NumberFormatException invalid) {
            throw invalid("The timetable contains an invalid date, time, time zone, duration or recurrence number.");
        }
    }

    private static String readText(Path source) throws IOException {
        if (!Files.isRegularFile(source)) throw invalid("Choose a readable iCalendar file.");
        byte[] bytes;
        try (var input = Files.newInputStream(source)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw invalid("Choose a timetable file of at most 4 MiB.");
        // Unfold bytes before decoding so a fold cannot split a UTF-8 character.
        var unfolded = new ByteArrayOutputStream(bytes.length);
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] == '\r' && index + 2 < bytes.length && bytes[index + 1] == '\n'
                    && isFold(bytes[index + 2])) {
                index += 2;
            } else if (bytes[index] == '\n' && index + 1 < bytes.length && isFold(bytes[index + 1])) {
                index++;
            } else {
                unfolded.write(bytes[index]);
            }
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(unfolded.toByteArray())).toString();
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (CharacterCodingException invalid) {
            throw invalid("The timetable is not valid UTF-8.");
        }
    }

    private static boolean isFold(byte value) {
        return value == ' ' || value == '\t';
    }

    private static List<Component> components(String text) throws IOException {
        var nesting = new ArrayDeque<String>();
        var result = new ArrayList<Component>();
        Component active = null;
        boolean started = false;
        boolean version = false;
        for (String line : text.split("\\r?\\n", -1)) {
            if (line.isBlank()) continue;
            if (line.length() > 32_768) throw invalid("A timetable property is too long.");
            Property property = property(line);
            switch (property.name()) {
                case "BEGIN" -> {
                    String name = property.value().toUpperCase(Locale.ROOT);
                    if (nesting.isEmpty()) {
                        if (started || !name.equals("VCALENDAR")) throw invalid("Choose a single iCalendar file.");
                        started = true;
                    }
                    if (name.equals("VEVENT")) {
                        if (!"VCALENDAR".equals(nesting.peek())) throw invalid("The timetable has a misplaced event.");
                        active = new Component(new HashMap<>());
                    }
                    if (nesting.size() == 16) throw invalid("The timetable components are nested too deeply.");
                    nesting.push(name);
                }
                case "END" -> {
                    if (nesting.isEmpty() || !nesting.pop().equalsIgnoreCase(property.value())) {
                        throw invalid("The timetable has invalid component boundaries.");
                    }
                    if (property.value().equalsIgnoreCase("VEVENT")) {
                        result.add(active);
                        active = null;
                        if (result.size() > MAX_COMPONENTS) throw invalid("The timetable contains too many entries.");
                    }
                }
                default -> {
                    if (nesting.isEmpty()) throw invalid("Choose a valid iCalendar file.");
                    if ("VEVENT".equals(nesting.peek())) {
                        active.properties().computeIfAbsent(property.name(), ignored -> new ArrayList<>()).add(property);
                    } else if ("VCALENDAR".equals(nesting.peek()) && property.name().equals("VERSION")) {
                        version = property.value().equals("2.0");
                    }
                }
            }
        }
        if (!started || !version || !nesting.isEmpty()) throw invalid("Choose a complete iCalendar 2.0 file.");
        return result;
    }

    private static Property property(String line) throws IOException {
        int separator = delimiter(line, ':');
        if (separator <= 0) throw invalid("The timetable contains a malformed property.");
        String header = line.substring(0, separator);
        int parameterStart = delimiter(header, ';');
        String name = (parameterStart < 0 ? header : header.substring(0, parameterStart)).toUpperCase(Locale.ROOT);
        Map<String, String> parameters = new HashMap<>();
        while (parameterStart >= 0) {
            header = header.substring(parameterStart + 1);
            parameterStart = delimiter(header, ';');
            String parameter = parameterStart < 0 ? header : header.substring(0, parameterStart);
            int equals = parameter.indexOf('=');
            if (equals <= 0) throw invalid("The timetable contains a malformed property parameter.");
            String value = parameter.substring(equals + 1);
            if (value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            if (parameters.putIfAbsent(parameter.substring(0, equals).toUpperCase(Locale.ROOT), value) != null) {
                throw invalid("A timetable property repeats a parameter.");
            }
            if (parameters.size() > 32) throw invalid("A timetable property has too many parameters.");
        }
        return new Property(name, parameters, line.substring(separator + 1));
    }

    private static int delimiter(String text, char delimiter) {
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '"') quoted = !quoted;
            else if (!quoted && value == delimiter) return index;
        }
        return -1;
    }

    private static void expand(String uid, List<Component> components, ZoneId displayZone, List<Event> events)
            throws IOException {
        Component master = null;
        Map<String, Component> overrides = new LinkedHashMap<>();
        for (Component component : components) {
            if (component.first("EXRULE") != null) throw invalid("This timetable uses an unsupported EXRULE.");
            Property recurrence = component.first("RECURRENCE-ID");
            if (recurrence == null) {
                master = newer(master, component);
            } else {
                if (recurrence.parameters().containsKey("RANGE")) {
                    throw invalid("This timetable uses a recurrence change that applies to future events.");
                }
                String key = moment(recurrence, displayZone).key();
                overrides.put(key, newer(overrides.get(key), component));
            }
        }
        DurationRule defaultDuration = null;
        Moment masterStart = null;
        if (master != null && !master.cancelled()) {
            masterStart = start(master, displayZone);
            defaultDuration = duration(master, masterStart, displayZone, null);
            Map<String, Moment> starts = recurringStarts(master, masterStart, displayZone);
            for (var occurrence : starts.entrySet()) {
                if (!overrides.containsKey(occurrence.getKey())) {
                    events.add(event(uid, occurrence.getKey(), master, null, occurrence.getValue(),
                            defaultDuration, displayZone));
                }
            }
        }
        if (master == null || !master.cancelled()) {
            for (var replacement : overrides.entrySet()) {
                Component component = replacement.getValue();
                if (!component.cancelled()) {
                    if (component.first("RRULE") != null || component.first("RDATE") != null) {
                        throw invalid("A replacement event cannot start its own repeat series.");
                    }
                    Moment first = start(component, displayZone);
                    DurationRule previous = masterStart != null && masterStart.allDay() != first.allDay()
                            ? null : defaultDuration;
                    events.add(event(uid, replacement.getKey(), component, master, first,
                            duration(component, first, displayZone, previous), displayZone));
                }
            }
        }
    }

    private static Component newer(Component existing, Component candidate) {
        return existing == null || candidate.sequence() >= existing.sequence() ? candidate : existing;
    }

    private static Moment start(Component component, ZoneId zone) throws IOException {
        Property value = component.first("DTSTART");
        if (value == null) throw invalid("A timetable event is missing its start date.");
        return moment(value, zone);
    }

    private static Map<String, Moment> recurringStarts(Component event, Moment first, ZoneId zone)
            throws IOException {
        Map<String, Moment> starts = new LinkedHashMap<>();
        starts.put(first.key(), first);
        List<Property> rules = event.properties().getOrDefault("RRULE", List.of());
        if (rules.size() > 1) throw invalid("A timetable entry specifies more than one repeat rule.");
        if (!rules.isEmpty()) {
            Repetition rule = repetition(rules.getFirst(), first);
            for (int index = 1; ; index++) {
                if (rule.count() != null && starts.size() >= rule.count()) break;
                Moment candidate = new Moment(first.local().plusDays(index), first.zone(), first.allDay(), first.floating());
                if (rule.until() != null && candidate.compare(rule.until()) > 0) break;
                if (index > MAX_SERIES_DAYS) throw invalid("A timetable repeat series extends beyond ten years.");
                if (rule.matches(first.local().toLocalDate(), candidate.local().toLocalDate())
                        && (candidate.allDay() || !candidate.zone().getRules().getValidOffsets(candidate.local()).isEmpty())) {
                    starts.putIfAbsent(candidate.key(), candidate);
                }
            }
        }
        for (Moment added : dates(event, "RDATE", first, zone)) {
            starts.putIfAbsent(added.key(), added);
            if (starts.size() > MAX_OCCURRENCES) throw invalid("This timetable expands to too many events.");
        }
        for (Moment omitted : dates(event, "EXDATE", first, zone)) {
            starts.remove(omitted.key());
        }
        return starts;
    }

    private static List<Moment> dates(Component event, String name, Moment first, ZoneId zone) throws IOException {
        List<Moment> values = new ArrayList<>();
        for (Property property : event.properties().getOrDefault(name, List.of())) {
            for (String value : property.value().split(",", -1)) {
                Moment decoded = moment(new Property(name, property.parameters(), value), zone);
                sameType(first, decoded);
                values.add(decoded);
                if (values.size() > MAX_OCCURRENCES) throw invalid("A timetable entry has too many exception dates.");
            }
        }
        return values;
    }

    private static Repetition repetition(Property property, Moment first) throws IOException {
        Map<String, String> fields = new HashMap<>();
        for (String field : property.value().split(";", -1)) {
            String[] pair = field.toUpperCase(Locale.ROOT).split("=", 2);
            if (pair.length != 2 || pair[1].isEmpty() || fields.putIfAbsent(pair[0], pair[1]) != null) {
                throw invalid("A timetable entry has a malformed repeat rule.");
            }
        }
        if (!Set.of("FREQ", "UNTIL", "COUNT", "INTERVAL", "BYDAY", "WKST").containsAll(fields.keySet())) {
            throw invalid("This timetable uses a repeat rule other than supported daily and weekly patterns.");
        }
        String frequency = fields.get("FREQ");
        if (!"WEEKLY".equals(frequency) && !"DAILY".equals(frequency)) {
            throw invalid("Only daily and weekly timetable repeat rules are supported.");
        }
        Integer count = fields.containsKey("COUNT") ? positive(fields.get("COUNT"), MAX_OCCURRENCES) : null;
        Moment until = fields.containsKey("UNTIL")
                ? moment(new Property("UNTIL", Map.of(), fields.get("UNTIL")), first.zone()) : null;
        if ((count == null) == (until == null)) {
            throw invalid("A timetable repeat rule requires either a finite end date or an occurrence count.");
        }
        if (until != null) {
            sameType(first, until);
            if (until.compare(first) < 0) throw invalid("A timetable repeat rule ends before its first event.");
        }
        int interval = positive(fields.getOrDefault("INTERVAL", "1"), MAX_SERIES_DAYS);
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (fields.containsKey("BYDAY")) {
            for (String day : fields.get("BYDAY").split(",", -1)) days.add(weekday(day));
        }
        boolean weekly = frequency.equals("WEEKLY");
        if (days.isEmpty() && weekly) days.add(first.local().getDayOfWeek());
        return new Repetition(weekly, interval, days, weekday(fields.getOrDefault("WKST", "MO")), count, until);
    }

    private static int positive(String value, int max) throws IOException {
        int number = Integer.parseInt(value);
        if (number < 1 || number > max) throw invalid("A timetable repeat count or interval is outside the supported range.");
        return number;
    }

    private static DayOfWeek weekday(String code) throws IOException {
        DayOfWeek day = WEEKDAYS.get(code);
        if (day == null) throw invalid("A timetable repeat rule contains an unsupported weekday.");
        return day;
    }

    private static Moment moment(Property property, ZoneId fallbackZone) throws IOException {
        String value = property.value();
        String type = property.parameters().get("VALUE");
        if ("DATE".equalsIgnoreCase(type) || (type == null && value.length() == 8)) {
            return new Moment(LocalDate.parse(value, DATE).atStartOfDay(), fallbackZone, true, false);
        }
        if (type != null && !"DATE-TIME".equalsIgnoreCase(type)) {
            throw invalid("This timetable contains an unsupported event date type.");
        }
        boolean utc = value.endsWith("Z");
        String tzid = property.parameters().get("TZID");
        ZoneId zone;
        try {
            zone = utc ? ZoneOffset.UTC : tzid == null ? fallbackZone : ZoneId.of(tzid);
        } catch (DateTimeException unknownZone) {
            throw invalid("The timetable uses an unknown time zone. Export it with an IANA time zone.");
        }
        LocalDateTime time = LocalDateTime.parse(utc ? value.substring(0, value.length() - 1) : value, DATE_TIME);
        return new Moment(time, zone, false, !utc && tzid == null);
    }

    private static DurationRule duration(Component event, Moment first, ZoneId zone, DurationRule fallback)
            throws IOException {
        Property end = event.first("DTEND");
        Property duration = event.first("DURATION");
        if (end != null && duration != null) throw invalid("A timetable event has both an end date and a duration.");
        DurationRule result;
        if (end != null) {
            Moment after = moment(end, zone);
            sameType(first, after);
            int order = after.compare(first);
            if (order < 0 || (first.allDay() && order == 0)) {
                throw invalid("A timetable event cannot end before it starts, and all-day events need at least one day.");
            }
            result = first.allDay()
                    ? new DurationRule(ChronoUnit.DAYS.between(first.local(), after.local()), Duration.ZERO)
                    : new DurationRule(0, Duration.between(first.zoned(), after.zoned()));
        } else if (duration != null) {
            var fields = DURATION.matcher(duration.value());
            if (!fields.matches()) throw invalid("A timetable event duration is invalid.");
            long days = Math.addExact(Math.multiplyExact(number(fields.group(1)), 7), number(fields.group(2)));
            Duration time = Duration.ofHours(number(fields.group(3))).plusMinutes(number(fields.group(4)))
                    .plusSeconds(number(fields.group(5)));
            boolean hasTime = fields.group(3) != null || fields.group(4) != null || fields.group(5) != null;
            boolean hasDays = fields.group(1) != null || fields.group(2) != null;
            if ((!hasTime && !hasDays) || (duration.value().contains("T") && !hasTime)
                    || (first.allDay() && (hasTime || days == 0))) {
                throw invalid("A timetable event duration is invalid.");
            }
            result = new DurationRule(days, time);
        } else if (fallback != null) {
            result = fallback;
        } else {
            result = new DurationRule(first.allDay() ? 1 : 0, Duration.ZERO);
        }
        if (result.days() > MAX_EVENT_DAYS || result.elapsed().compareTo(Duration.ofDays(MAX_EVENT_DAYS)) > 0
                || Duration.ofDays(result.days()).plus(result.elapsed()).compareTo(Duration.ofDays(MAX_EVENT_DAYS)) > 0) {
            throw invalid("A single timetable event can span at most 366 days.");
        }
        return result;
    }

    private static long number(String value) {
        return value == null ? 0 : Long.parseLong(value);
    }

    private static void sameType(Moment first, Moment later) throws IOException {
        if (first.allDay() != later.allDay()) throw invalid("A timetable event mixes date and date-time values.");
    }

    private static Event event(String uid, String key, Component details, Component fallback, Moment first,
                               DurationRule duration, ZoneId displayZone) throws IOException {
        String title = preferredText(details, fallback, "SUMMARY");
        LocalDateTime shownStart = first.allDay() ? first.local() : first.zoned().withZoneSameInstant(displayZone)
                .toLocalDateTime();
        LocalDateTime shownEnd = first.allDay() ? shownStart.plusDays(duration.days())
                : first.zoned().plusDays(duration.days()).plus(duration.elapsed()).withZoneSameInstant(displayZone)
                        .toLocalDateTime();
        if (shownEnd.isBefore(shownStart)) {
            throw invalid("A timetable event's local end time falls before its start during a time-zone change.");
        }
        return new Event(identifier(uid, key), title.isBlank() ? "Untitled event" : title,
                preferredText(details, fallback, "DESCRIPTION"), preferredText(details, fallback, "LOCATION"),
                shownStart, shownEnd, first.allDay());
    }

    private static String preferredText(Component details, Component fallback, String key) {
        return details.first(key) != null || fallback == null ? details.text(key) : fallback.text(key);
    }

    private static String identifier(String uid, String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (uid.length() + ":" + uid + key).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static IOException invalid(String message) {
        return new IOException(message);
    }

    public record Event(String id, String title, String description, String location, LocalDateTime start,
                        LocalDateTime end, boolean allDay) { }

    private record Property(String name, Map<String, String> parameters, String value) { }

    private record Component(Map<String, List<Property>> properties) {
        Property first(String name) {
            List<Property> entries = properties.get(name);
            return entries == null ? null : entries.getFirst();
        }

        String text(String name) {
            Property property = first(name);
            if (property == null) return "";
            StringBuilder text = new StringBuilder();
            String value = property.value();
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                if (current == '\\' && index + 1 < value.length()) {
                    char escaped = value.charAt(index + 1);
                    switch (escaped) {
                        case 'n', 'N' -> { text.append('\n'); index++; continue; }
                        case '\\', ',', ';' -> { text.append(escaped); index++; continue; }
                        default -> { }
                    }
                }
                text.append(current);
            }
            return text.toString().strip();
        }

        boolean cancelled() { return "CANCELLED".equalsIgnoreCase(text("STATUS")); }

        int sequence() {
            Property entry = first("SEQUENCE");
            return entry == null ? 0 : Integer.parseInt(entry.value());
        }
    }

    private record Moment(LocalDateTime local, ZoneId zone, boolean allDay, boolean floating) {
        ZonedDateTime zoned() { return local.atZone(zone); }

        String key() {
            if (allDay) return "date:" + local.toLocalDate();
            return floating ? "floating:" + local : "time:" + zoned().toInstant();
        }

        int compare(Moment other) {
            return allDay ? local.compareTo(other.local()) : zoned().toInstant().compareTo(other.zoned().toInstant());
        }
    }

    private record DurationRule(long days, Duration elapsed) { }

    private record Repetition(boolean weekly, int interval, Set<DayOfWeek> days, DayOfWeek weekStart,
                              Integer count, Moment until) {
        boolean matches(LocalDate first, LocalDate candidate) {
            if (!days.isEmpty() && !days.contains(candidate.getDayOfWeek())) return false;
            long distance = weekly
                    ? ChronoUnit.DAYS.between(first.with(TemporalAdjusters.previousOrSame(weekStart)), candidate) / 7
                    : ChronoUnit.DAYS.between(first, candidate);
            return distance % interval == 0;
        }
    }
}
