package com.pension.engine.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CalculationMessage {

    @JsonProperty("id")
    private int id;

    @JsonProperty("level")
    private String level;

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;

    public CalculationMessage() {}

    public CalculationMessage(String level, String code, String message) {
        this.level = level;
        this.code = code;
        this.message = message;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
