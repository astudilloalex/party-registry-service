package com.alexastudillo.partyregistry.application;

public record OutboxBatchResult(int claimed, int processed, int staleOutcomes) {}
