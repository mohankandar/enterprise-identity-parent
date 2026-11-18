package com.documentplatform.identity.mapping;

import com.documentplatform.identity.admission.EntityId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * What loading a valid file would change. The file is the complete record for every entity it names: for those
 * entities the grants become exactly the file's rows (missing ones added, others removed). Entities not in the file
 * are not touched. Loading the same file twice gives a plan with no changes.
 */
public record MappingPlan(List<EntityChange> entities) {

    /** The change for one entity named in the file. */
    public record EntityChange(EntityId entity, String name, Set<Grant> toAdd, Set<Grant> toRemove, int unchanged) {
        public boolean changes() {
            return !toAdd.isEmpty() || !toRemove.isEmpty();
        }
    }

    public boolean hasChanges() {
        return entities.stream().anyMatch(EntityChange::changes);
    }

    /**
     * @param rows    the rows of a valid file
     * @param current the grants each entity has now, read from the product's store
     */
    public static MappingPlan between(List<MappingRow> rows, Function<EntityId, Set<Grant>> current) {
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(current, "current");
        Map<EntityId, Set<Grant>> wanted = new LinkedHashMap<>();
        Map<EntityId, String> names = new LinkedHashMap<>();
        for (MappingRow row : rows) {
            wanted.computeIfAbsent(row.entity(), e -> new LinkedHashSet<>()).add(row.grant());
            names.putIfAbsent(row.entity(), row.entityName());
        }
        List<EntityChange> changes = new ArrayList<>();
        for (Map.Entry<EntityId, Set<Grant>> entry : wanted.entrySet()) {
            Set<Grant> now = Objects.requireNonNullElse(current.apply(entry.getKey()), Set.of());
            Set<Grant> toAdd = new LinkedHashSet<>(entry.getValue());
            toAdd.removeAll(now);
            Set<Grant> toRemove = new LinkedHashSet<>(now);
            toRemove.removeAll(entry.getValue());
            int unchanged = entry.getValue().size() - toAdd.size();
            changes.add(new EntityChange(entry.getKey(), names.get(entry.getKey()), sorted(toAdd), sorted(toRemove), unchanged));
        }
        return new MappingPlan(List.copyOf(changes));
    }

    private static Set<Grant> sorted(Set<Grant> grants) {
        List<Grant> list = new ArrayList<>(grants);
        list.sort(Comparator.comparing(Grant::group).thenComparing(Grant::role));
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(list));
    }
}
