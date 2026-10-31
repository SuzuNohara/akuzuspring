package com.nexus.service;

import com.nexus.service.FreeTimeNotifier.FreeSlot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * [libres-juntos] Que Nexus proponga cuando verse en vez de listar huecos en crudo. La misma regla
 * vive en la app (utils/freeProposals.ts); las dos se prueban con free-proposals.json.
 *
 * <p>Los huecos de los dos se recortan a partir de ahora, el inicio sube y el fin baja a :00/:30, se
 * parten en manana (antes de 13), tarde (13-19) y noche (desde 19) y solo quedan los de 1 h o mas.
 * Se propone uno por dia: el mas largo de tarde o noche (si no hay, el de la manana).
 */
final class FreeTimeProposals {

    enum Part { MORNING, AFTERNOON, EVENING }

    /** Un pedazo limpio de un hueco libre de los dos. */
    record Window(LocalDate date, LocalTime start, LocalTime end, Part part) {
        int minutes() {
            return (int) ChronoUnit.MINUTES.between(start, end);
        }
    }

    /** "Sábado en la tarde" y "4 h libres". */
    record Label(String when, String length) {}

    private static final int MIN_MINUTES = 60;
    private static final int STEP = 30;
    private static final int AFTERNOON_FROM = 13 * 60;
    private static final int EVENING_FROM = 19 * 60;
    private static final String[] WEEKDAYS = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
    private static final Map<Part, String> PART_TEXT = Map.of(
            Part.MORNING, "por la mañana", Part.AFTERNOON, "en la tarde", Part.EVENING, "en la noche");

    private FreeTimeProposals() {}

    /** Huecos limpios; con {@code split = false} cada hueco queda entero. Ordenados por dia y hora. */
    static List<Window> windowsOf(List<FreeSlot> slots, LocalDateTime now, boolean split) {
        LocalDate today = now.toLocalDate();
        int nowMinutes = now.getHour() * 60 + now.getMinute();
        List<Window> out = new ArrayList<>();
        for (FreeSlot slot : slots) {
            if (slot.date().isBefore(today)) {
                continue;
            }
            int start = minutes(slot.start());
            if (slot.date().equals(today)) {
                start = Math.max(start, nowMinutes);
            }
            start = (start + STEP - 1) / STEP * STEP;
            int end = minutes(slot.end()) / STEP * STEP;
            List<Integer> cuts = new ArrayList<>();
            cuts.add(start);
            if (split) {
                for (int cut : new int[] {AFTERNOON_FROM, EVENING_FROM}) {
                    if (cut > start && cut < end) {
                        cuts.add(cut);
                    }
                }
            }
            cuts.add(end);
            for (int i = 0; i < cuts.size() - 1; i++) {
                if (cuts.get(i + 1) - cuts.get(i) >= MIN_MINUTES) {
                    out.add(new Window(slot.date(), clock(cuts.get(i)), clock(cuts.get(i + 1)), partOf(cuts.get(i))));
                }
            }
        }
        out.sort(Comparator.comparing(Window::date).thenComparing(Window::start));
        return out;
    }

    /** Una por dia, en los primeros {@code max} dias. */
    static List<Window> proposalsOf(List<Window> windows, int max) {
        Map<LocalDate, List<Window>> byDay = new TreeMap<>();
        for (Window w : windows) {
            byDay.computeIfAbsent(w.date(), d -> new ArrayList<>()).add(w);
        }
        List<Window> out = new ArrayList<>();
        for (List<Window> day : byDay.values()) {
            Optional<Window> best = longest(day.stream().filter(w -> w.part() != Part.MORNING).toList());
            (best.isPresent() ? best : longest(day)).ifPresent(out::add);
            if (out.size() == max) {
                break;
            }
        }
        return out;
    }

    /** Hoy y mañana por nombre; de 2 a 6 dias, el nombre del dia; a una semana o mas, tambien el numero. */
    static Label label(Window w, LocalDate today) {
        long daysAway = ChronoUnit.DAYS.between(today, w.date());
        String weekday = WEEKDAYS[w.date().getDayOfWeek().getValue() - 1];
        String day = daysAway == 0 ? "Hoy" : daysAway == 1 ? "Mañana"
                : daysAway < 7 ? weekday : weekday + " " + w.date().getDayOfMonth();
        int hours = w.minutes() / 60;
        int rest = w.minutes() % 60;
        String length = (hours > 0 ? hours + " h" : "") + (hours > 0 && rest > 0 ? " " : "") + (rest > 0 ? rest + " min" : "");
        return new Label(day + " " + PART_TEXT.get(w.part()), length + " libres");
    }

    private static Optional<Window> longest(List<Window> list) {
        // A igual duracion gana la primera (la mas temprana), como en la app.
        Window best = null;
        for (Window w : list) {
            if (best == null || w.minutes() > best.minutes()) {
                best = w;
            }
        }
        return Optional.ofNullable(best);
    }

    private static int minutes(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    private static LocalTime clock(int minutes) {
        return LocalTime.of(minutes / 60, minutes % 60);
    }

    private static Part partOf(int start) {
        return start < AFTERNOON_FROM ? Part.MORNING : start < EVENING_FROM ? Part.AFTERNOON : Part.EVENING;
    }
}
