package com.rodrigs.finance_manager_api.transaction.rag;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class TransactionEmbeddingText {
    private static final Pattern EMAIL = Pattern.compile("(?iu)[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[\\p{L}]{2,}");
    private static final Pattern DOCUMENT = Pattern.compile("(?iu)(?<![0-9])(?:\\b(?:CPF|CNPJ)\\s*[:#-]?\\s*)?"
            + "(?:[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}|[0-9]{2}\\.[0-9]{3}\\.[0-9]{3}/[0-9]{4}-[0-9]{2}"
            + "|[0-9]{11}|[0-9]{14})(?![0-9])");
    private static final Pattern PHONE = Pattern.compile(
            "(?<![0-9])(?:\\+55[ .-]*)?(?:\\([0-9]{2}\\)|[0-9]{2})[ .-]*[0-9]{4,5}[ .-]*[0-9]{4}(?![0-9])");
    private static final Pattern ACCOUNT_IDENTIFIER = Pattern.compile(
            "(?iu)\\b(?:conta(?:\\s+corrente)?|ag[êe]ncia|chave\\s+pix|cart[ãa]o)"
                    + "(?:\\s+(?:n[º°o.]|n[uú]mero))?\\s*[:#-]?\\s*[0-9][0-9 .-]*[0-9](?![0-9])");
    private static final Pattern UUID_IDENTIFIER = Pattern.compile(
            "(?iu)(?:\\bchave\\s+pix\\s*[:#-]?\\s*)?"
                    + "\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");
    private static final Pattern CARD_IDENTIFIER = Pattern.compile("(?<![0-9])(?:[0-9]{4}[- ]?){3}[0-9]{4}(?![0-9])");
    private static final Pattern DATE = Pattern.compile("(?<![0-9])(?:[0-9]{4}-[0-9]{2}-[0-9]{2}"
            + "|[0-9]{1,2}/[0-9]{1,2}(?:/[0-9]{2,4})?)(?![0-9/])");
    private static final String AMOUNT = "[0-9]+(?:[.,][0-9]+)*";
    private static final Pattern MONEY = Pattern.compile(
            "(?iu)(?:R\\s*\\$|BRL\\s+)\\s*" + AMOUNT + "|\\b" + AMOUNT + "\\s+rea(?:l|is)\\b"
                    + "|(?<![0-9.,])(?:[0-9]{1,3}(?:\\.[0-9]{3})+|[0-9]+)[.,][0-9]{2}(?![0-9.,])");
    private static final Pattern BARE_AMOUNT = Pattern.compile(
            "(?iu)(\\b(?:paguei|gastei|recebi|custou|depositei|transferi|cobrou|rendeu|ganhei)\\s+)"
                    + AMOUNT + "(?![\\p{L}\\p{N}.,])");
    private static final Pattern SPACING = Pattern.compile("(?U)\\s+");
    private static final Pattern EDGE_SEPARATORS = Pattern.compile("^[,;:/#-]+\\s*|\\s*[,;:/#-]+$");

    private TransactionEmbeddingText() {
    }

    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        normalized = EMAIL.matcher(normalized).replaceAll(" ");
        normalized = UUID_IDENTIFIER.matcher(normalized).replaceAll(" ");
        normalized = ACCOUNT_IDENTIFIER.matcher(normalized).replaceAll(" ");
        normalized = CARD_IDENTIFIER.matcher(normalized).replaceAll(" ");
        normalized = DOCUMENT.matcher(normalized).replaceAll(" ");
        normalized = PHONE.matcher(normalized).replaceAll(" ");
        normalized = DATE.matcher(normalized).replaceAll(" ");
        normalized = MONEY.matcher(normalized).replaceAll(" ");
        normalized = BARE_AMOUNT.matcher(normalized).replaceAll("$1");
        normalized = SPACING.matcher(normalized).replaceAll(" ").strip();
        normalized = EDGE_SEPARATORS.matcher(normalized).replaceAll("");
        return normalized.codePoints().anyMatch(Character::isLetter) ? normalized : "";
    }
}
