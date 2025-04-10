package org.archipel.extractors;

import com.google.gson.JsonElement;

public interface IExtractor {
	JsonElement extract();
	String getName();
}
