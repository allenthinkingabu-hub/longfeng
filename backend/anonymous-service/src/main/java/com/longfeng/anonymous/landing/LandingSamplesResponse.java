package com.longfeng.anonymous.landing;

import java.util.List;

/**
 * Response body of {@code GET /api/landing/samples}.
 *
 * @param bucket  experiment bucket label that selected this sample set
 * @param samples sample card list (currently 3 hard-coded — see TODO in {@link LandingController})
 */
public record LandingSamplesResponse(String bucket, List<SampleCardDto> samples) {}
