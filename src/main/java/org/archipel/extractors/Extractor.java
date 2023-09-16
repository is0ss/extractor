package org.archipel.extractors;

import com.google.gson.JsonElement;

public interface Extractor {
    JsonElement extract();
    String getName();
}
