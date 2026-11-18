package com.documentplatform.identity.mapping;

import com.documentplatform.identity.admission.EntityId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappingPlanTest {

    private static final EntityId A = EntityId.of(UUID.randomUUID());
    private static final EntityId B = EntityId.of(UUID.randomUUID());

    private static MappingRow row(EntityId entity, String group, String role) {
        return new MappingRow(2, entity, entity == A ? "A" : "B", group, role);
    }

    @Test
    void theFileBecomesTheEntitysCompleteRecord() {
        Map<EntityId, Set<Grant>> now = Map.of(A, Set.of(new Grant("keep", "READER"), new Grant("old", "EDITOR")));
        MappingPlan plan = MappingPlan.between(
                List.of(row(A, "keep", "READER"), row(A, "new", "EDITOR")),
                e -> now.getOrDefault(e, Set.of()));
        assertTrue(plan.hasChanges());
        MappingPlan.EntityChange a = plan.entities().get(0);
        assertEquals(Set.of(new Grant("new", "EDITOR")), a.toAdd());
        assertEquals(Set.of(new Grant("old", "EDITOR")), a.toRemove());
        assertEquals(1, a.unchanged());
        assertEquals("A", a.name());
    }

    @Test
    void theSameFileTwiceChangesNothing() {
        List<MappingRow> rows = List.of(row(A, "g", "READER"), row(B, "g", "EDITOR"));
        Map<EntityId, Set<Grant>> now = Map.of(A, Set.of(new Grant("g", "READER")), B, Set.of(new Grant("g", "EDITOR")));
        MappingPlan plan = MappingPlan.between(rows, now::get);
        assertFalse(plan.hasChanges());
        assertEquals(2, plan.entities().size());
        assertTrue(plan.entities().stream().noneMatch(MappingPlan.EntityChange::changes));
    }

    @Test
    void entitiesNotInTheFileAreNotTouched() {
        MappingPlan plan = MappingPlan.between(List.of(row(A, "g", "READER")),
                e -> e.equals(B) ? Set.of(new Grant("b", "READER")) : Set.of());
        assertEquals(List.of(A), plan.entities().stream().map(MappingPlan.EntityChange::entity).toList());
    }

    @Test
    void aNewEntityGetsEveryRowAdded() {
        MappingPlan plan = MappingPlan.between(List.of(row(A, "z", "READER"), row(A, "a", "READER")), e -> null);
        assertEquals(List.of(new Grant("a", "READER"), new Grant("z", "READER")), List.copyOf(plan.entities().get(0).toAdd()));
        assertEquals(0, plan.entities().get(0).unchanged());
    }

    @Test
    void groupsAreComparedExactly() {
        MappingPlan plan = MappingPlan.between(List.of(row(A, "Readers", "READER")),
                e -> Set.of(new Grant("readers", "READER")));
        assertEquals(Set.of(new Grant("Readers", "READER")), plan.entities().get(0).toAdd());
        assertEquals(Set.of(new Grant("readers", "READER")), plan.entities().get(0).toRemove());
    }
}
