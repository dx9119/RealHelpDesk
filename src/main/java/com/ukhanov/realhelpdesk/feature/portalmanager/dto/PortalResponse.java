package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import java.time.Instant;

public record PortalResponse(Long id, String name, String description, Instant createdAt) {

    public static class PortalResponseBuilder {
        private Long id;
        private String name;
        private String description;
        private Instant createdAt;

        public PortalResponseBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public PortalResponseBuilder name(String name) {
            this.name = name;
            return this;
        }

        public PortalResponseBuilder description(String description) {
            this.description = description;
            return this;
        }

        public PortalResponseBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public PortalResponse build() {
            return new PortalResponse(id, name, description, createdAt);
        }
    }
}
