package com.studyflow.interfaces;

import java.io.IOException;

/** Optional external text-generation adapter. */
public interface AIProvider {
    boolean configured();
    String generate(String prompt) throws IOException;
}
