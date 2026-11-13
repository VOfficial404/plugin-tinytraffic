package io.github.vofficial404.tinytraffic.client.model;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/** Visitors seen in the last five minutes. */
@Schema(name = "TinytrafficRealtime")
@JsonIgnoreProperties(ignoreUnknown = true)
public record Realtime(@Schema(requiredMode = REQUIRED) long visitors) {}
