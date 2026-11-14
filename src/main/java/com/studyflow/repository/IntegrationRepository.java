package com.studyflow.repository;

import com.studyflow.integrations.LocalIntegrations.Link;
import java.nio.file.Path;

public final class IntegrationRepository extends JsonCollectionRepository<Link, Link> {
    public IntegrationRepository(FileStorage storage) {
        super(storage, Path.of("integrations", "links.json"), Link.class, link -> link, link -> link);
    }
}
