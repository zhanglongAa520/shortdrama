package com.example.drama;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class SeedMarker {
    @Id
    private String id;

    protected SeedMarker() {}

    public SeedMarker(String id) { this.id = id; }
}
