package com.studyflow.service;

import com.studyflow.service.MaterialLibrary.Passage;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.regex.Pattern;

/** Ranks exact Unicode keyword matches and keeps one best excerpt per page. */
final class PassageRanker {
    private static final Pattern WORD = Pattern.compile("\\p{IsHan}|[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}]*");
    private static final Set<String> COMMON = Set.of("a", "an", "and", "are", "as", "be", "explain", "for",
            "from", "how", "in", "is", "it", "of", "on", "or", "the", "to", "what", "when", "where", "with");
    private static final int MAX_RESULTS = 12;
    private static final Comparator<Match> BEST = Comparator.comparingDouble(Match::score).reversed()
            .thenComparing(match -> match.passage().fileName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(match -> match.passage().materialId())
            .thenComparingInt(match -> match.passage().page());

    private final List<String> sequence;
    private final Set<String> keywords;
    private final boolean browse;
    private final PriorityQueue<Match> best = new PriorityQueue<>(BEST.reversed());
    private Match bestOnPage;
    private int page;

    PassageRanker(String query) {
        String text = query == null ? "" : query.strip();
        browse = text.isEmpty();
        sequence = words(text).stream().filter(word -> !COMMON.contains(word)).limit(32).toList();
        keywords = new LinkedHashSet<>(sequence);
    }

    void accept(Passage passage) {
        if (page != passage.page()) {
            finishPage();
            page = passage.page();
        }
        double score = score(passage.text());
        if (score >= 0 && (bestOnPage == null || score > bestOnPage.score())) {
            bestOnPage = new Match(score, passage);
        }
    }

    List<Match> matches() {
        finishPage();
        return best.stream().sorted(BEST).toList();
    }

    void merge(PassageRanker other) {
        other.matches().forEach(this::keep);
    }

    String preview(String text) {
        if (text.isEmpty()) {
            return text;
        }
        int anchor = 0;
        var tokens = WORD.matcher(text);
        while (tokens.find()) {
            if (keywords.contains(normalize(tokens.group()))) {
                anchor = tokens.start();
                break;
            }
        }
        int start = Math.max(0, anchor - 60);
        int end = Math.min(text.length(), Math.max(190, anchor + 130));
        if (start > 0 && Character.isLowSurrogate(text.charAt(start))) {
            start--;
        }
        if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return (start > 0 ? "…" : "") + text.substring(start, end).replaceAll("(?U)\\s+", " ").strip()
                + (end < text.length() ? "…" : "");
    }

    private double score(String text) {
        if (browse) {
            return 0;
        }
        if (keywords.isEmpty()) {
            return -1;
        }
        List<String> body = words(text);
        Map<String, Integer> frequency = new HashMap<>();
        for (String word : body) {
            if (keywords.contains(word)) {
                frequency.merge(word, 1, Integer::sum);
            }
        }
        if (frequency.isEmpty()) {
            return -1;
        }
        int repetitions = frequency.values().stream().mapToInt(count -> Math.min(3, count)).sum();
        int phrase = sequence.size() > 1 && Collections.indexOfSubList(body, sequence) >= 0 ? 4 : 0;
        return 16 * frequency.size() + phrase + 0.15 * repetitions + frequency.size() / (1 + body.size() / 80.0);
    }

    private void finishPage() {
        if (bestOnPage != null) {
            keep(bestOnPage);
            bestOnPage = null;
        }
    }

    private void keep(Match match) {
        best.add(match);
        if (best.size() > MAX_RESULTS) {
            best.remove();
        }
    }

    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        WORD.matcher(normalize(text)).results().forEach(word -> words.add(word.group()));
        return words;
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    record Match(double score, Passage passage) { }
}
