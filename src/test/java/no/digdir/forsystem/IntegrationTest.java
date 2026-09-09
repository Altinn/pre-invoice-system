package no.digdir.forsystem;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base for integration tests: full context on a shared PostgreSQL 16 Testcontainer (docs/05 — no
 * H2). {@code @Transactional} rolls back each test so they stay isolated while sharing the seeded
 * schema. The context is cached across subclasses because the configuration is identical.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
public abstract class IntegrationTest {
}
