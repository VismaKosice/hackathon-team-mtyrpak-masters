package com.pension.engine.model.state;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Situation {

    @JsonProperty("dossier")
    private Dossier dossier;

    public Situation() {}

    public Situation(Dossier dossier) {
        this.dossier = dossier;
    }

    public Dossier getDossier() { return dossier; }
    public void setDossier(Dossier dossier) { this.dossier = dossier; }
}
