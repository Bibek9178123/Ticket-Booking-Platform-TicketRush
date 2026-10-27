package com.ticketrush.event.catalog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketmasterEventDto(
        String id,
        String name,
        List<TmImage> images,
        TmDates dates,
        List<TmClassification> classifications,
        @JsonProperty("_embedded") TmEmbedded embedded
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TmImage(String ratio, String url, Integer width, Integer height) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TmDates(TmStart start) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TmStart(String localDate, String localTime, String dateTime) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TmClassification(TmNamed segment, TmNamed genre, TmNamed subGenre) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TmNamed(String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TmEmbedded(List<TmVenue> venues) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TmVenue(String name, TmNamed city, TmNamed state, TmNamed country) {}

    public String getBestImageUrl() {
        if (images == null || images.isEmpty()) return null;
        return images.stream()
                .filter(img -> "16_9".equalsIgnoreCase(img.ratio()) || (img.width() != null && img.width() >= 600))
                .map(TmImage::url)
                .findFirst()
                .orElse(images.get(0).url());
    }

    public String getVenueName() {
        if (embedded != null && embedded.venues() != null && !embedded.venues().isEmpty()) {
            return embedded.venues().get(0).name();
        }
        return "National Arena";
    }

    public String getCityName() {
        if (embedded != null && embedded.venues() != null && !embedded.venues().isEmpty()) {
            TmVenue v = embedded.venues().get(0);
            if (v.city() != null && v.city().name() != null) return v.city().name();
        }
        return "Metro City";
    }

    public String getCategory() {
        if (classifications != null && !classifications.isEmpty()) {
            String seg = classifications.get(0).segment() != null ? classifications.get(0).segment().name() : "";
            if ("Music".equalsIgnoreCase(seg)) return "Concerts";
            if ("Sports".equalsIgnoreCase(seg)) return "Sports";
            if ("Arts & Theatre".equalsIgnoreCase(seg)) return "Theater";
        }
        return "Concerts";
    }
}
