package com.rodrigs.finance_manager_api.transaction.rag;

import java.text.Normalizer;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class LocalTransactionEmbeddingClient implements TransactionEmbeddingClient {
    static final int DIMENSIONS = 768;
    private static final int WORD_DIMENSIONS = 512;
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private static final Pattern WORD = Pattern.compile("\\p{L}{2,}");
    private static final Set<String> STOP_WORDS = Set.of(
            "ao", "as", "com", "da", "das", "de", "do", "dos", "em", "na", "nas", "no", "nos",
            "os", "para", "por", "sem", "um", "uma", "hoje", "ontem", "reais", "brl",
            "comprei", "gastei", "paguei", "recebi", "custou", "depositei", "transferi", "cobrou", "rendeu", "ganhei");

    @Override
    public float[] embedDocument(String text) {
        return embed(text);
    }

    @Override
    public float[] embedQuery(String text, Duration timeout) {
        return embed(text);
    }

    private float[] embed(String text) {
        String folded = ACCENTS.matcher(Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD))
                .replaceAll("");
        var words = WORD.matcher(folded);
        var seen = new HashSet<String>();
        float[] vector = new float[DIMENSIONS];
        while (words.find()) {
            String word = words.group();
            if (STOP_WORDS.contains(word) || !seen.add(word)) {
                continue;
            }
            add(vector, word, 0, WORD_DIMENSIONS, 2f);
            // Subwords give spelling and inflection variants a smaller contribution than an exact word.
            for (int i = 0; i <= word.length() - 3; i++) {
                add(vector, word.substring(i, i + 3), WORD_DIMENSIONS, DIMENSIONS - WORD_DIMENSIONS, 0.5f);
            }
        }
        return vector;
    }

    private void add(float[] vector, String term, int offset, int dimensions, float weight) {
        int hash = term.hashCode();
        vector[offset + Math.floorMod(hash, dimensions)] += (Integer.bitCount(hash) & 1) == 0 ? weight : -weight;
    }
}
