package com.studyflow.interfaces;

import java.io.IOException;

/** Optional external text-generation adapter. */
public interface AIProvider {
    boolean configured();
    String generate(String prompt) throws IOException;

    default String generate(String instructions, String input) throws IOException {
        return generate(instructions + "\n\n" + input);
    }

    default String generateStructured(String prompt, String schemaName, String schema) throws IOException {
        throw new IOException("The configured AI provider does not support structured question sets.");
    }

    default String generateStructured(String instructions, String input, String schemaName, String schema)
            throws IOException {
        return generateStructured(instructions + "\n\n" + input, schemaName, schema);
    }
}
