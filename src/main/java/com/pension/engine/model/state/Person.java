package com.pension.engine.model.state;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Person {

    @JsonProperty("person_id")
    private String personId;

    @JsonProperty("role")
    private String role;

    @JsonProperty("name")
    private String name;

    @JsonProperty("birth_date")
    private String birthDate;

    public Person() {}

    public Person(String personId, String role, String name, String birthDate) {
        this.personId = personId;
        this.role = role;
        this.name = name;
        this.birthDate = birthDate;
    }

    public String getPersonId() { return personId; }
    public void setPersonId(String personId) { this.personId = personId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBirthDate() { return birthDate; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }
}
