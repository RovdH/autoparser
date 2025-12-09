package nl.wijnkado.autoparse.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductDto {

    private Long id;
    private String name;

    @JsonProperty("short_description")
    private String shortDescription;

    private String description;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Handige helper: kies short_description als die er is, anders description.
     */
public String getBestDescription() {
    // Voorkeur: lange beschrijving (tab "Beschrijving")
    if (description != null && !description.isBlank()) {
        return description;
    }

    // Fallback: korte omschrijving
    if (shortDescription != null && !shortDescription.isBlank()) {
        return shortDescription;
    }

    // Als beide leeg zijn
    return "";
}

}
