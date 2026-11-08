package com.rodrigs.finance_manager_api.transaction.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

final class TransactionChatDates {
    private static final List<String> MONTHS = List.of("janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro");
    private static final Pattern DATE_TOKEN = Pattern.compile(
            "(?<![\\p{L}\\d/])(?:"
                    + "(?<iso>\\d{4}-\\d{1,2}-\\d{1,2})"
                    + "|(?<day>\\d{1,2})/(?<month>\\d{1,2})(?:/(?<year>\\d{1,4}))?"
                    + "(?!/|\\s*(?:kg|g|l|ml|quilos?|litros?|parcelas?)\\b)"
                    + "|(?<longDay>\\d{1,2})\\s+de\\s+(?<longMonth>" + String.join("|", MONTHS)
                    + ")(?:\\s+de\\s+(?<longYear>\\d{2,4}))?"
                    + "|(?<relative>hoje|ontem|anteontem|antes de ontem)"
                    + "|amanh[ãa]|(?:segunda|ter[çc]a|quarta|quinta|sexta)-feira|s[áa]bado|domingo"
                    + "|semana passada|mês passado|dia\\s+\\d{1,2}(?![/\\d]|\\s+de\\s)"
                    + ")(?![\\p{L}\\d])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern LEADING_DATE_PREFIX = Pattern.compile(
            "(?iu)\\s*(?:(?:paguei|gastei|comprei|recebi|ganhei)\\s+)?(?:(?:em|no dia)\\s+)?");

    private TransactionChatDates() {
    }

    static String withoutDates(String text) {
        return DATE_TOKEN.matcher(text).replaceAll(" ");
    }

    static Evidence find(String text, LocalDate referenceDate, LocalDate latestDate) {
        return resolve(DATE_TOKEN.matcher(text).results().toList(), referenceDate, latestDate);
    }

    static Evidence leading(String text, LocalDate referenceDate) {
        var tokens = DATE_TOKEN.matcher(text);
        if (tokens.find() && LEADING_DATE_PREFIX.matcher(text).region(0, tokens.start()).matches()) {
            return resolve(List.of(tokens.toMatchResult()), referenceDate, referenceDate);
        }
        return Evidence.ABSENT;
    }

    private static Evidence resolve(List<MatchResult> tokens, LocalDate referenceDate, LocalDate latestDate) {
        LocalDate result = null;
        for (MatchResult token : tokens) {
            LocalDate date = parse(token, referenceDate);
            LocalDate limit = referenceDate == null ? latestDate : referenceDate;
            if (date == null || limit == null || date.isAfter(limit) || (result != null && !date.equals(result))) {
                return Evidence.INVALID;
            }
            result = date;
        }
        return new Evidence(result, false);
    }

    private static LocalDate parse(MatchResult token, LocalDate referenceDate) {
        try {
            String relative = token.group("relative");
            if (relative != null) {
                if (referenceDate == null) return null;
                return switch (relative.toLowerCase(Locale.ROOT)) {
                    case "ontem" -> referenceDate.minusDays(1);
                    case "anteontem", "antes de ontem" -> referenceDate.minusDays(2);
                    default -> referenceDate;
                };
            }

            String iso = token.group("iso");
            if (iso != null) {
                LocalDate date = LocalDate.parse(iso);
                return date.getYear() == 0 ? null : date;
            }

            String day = token.group("day");
            if (day != null) {
                return calendarDate(Integer.parseInt(day), Integer.parseInt(token.group("month")),
                        token.group("year"), referenceDate);
            }

            String longDay = token.group("longDay");
            if (longDay != null) {
                int month = MONTHS.indexOf(token.group("longMonth").toLowerCase(Locale.ROOT)) + 1;
                return calendarDate(Integer.parseInt(longDay), month, token.group("longYear"), referenceDate);
            }
            return null;
        } catch (DateTimeException exception) {
            return null;
        }
    }

    private static LocalDate calendarDate(int day, int month, String year, LocalDate referenceDate) {
        if (year != null) {
            if (year.length() != 4 || "0000".equals(year)) return null;
            return LocalDate.of(Integer.parseInt(year), month, day);
        }
        if (referenceDate == null) return null;
        LocalDate candidate = LocalDate.of(referenceDate.getYear(), month, day);
        return candidate.isAfter(referenceDate) ? LocalDate.of(referenceDate.getYear() - 1, month, day) : candidate;
    }

    record Evidence(LocalDate date, boolean invalid) {
        private static final Evidence ABSENT = new Evidence(null, false);
        private static final Evidence INVALID = new Evidence(null, true);

        boolean present() {
            return invalid || date != null;
        }
    }
}
