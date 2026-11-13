package com.studyflow.strategy;

import com.studyflow.model.Task;
import java.util.Comparator;

public final class SortByTitle implements TaskSortStrategy {
    private static final Comparator<Task> ORDER = Comparator.comparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Task::getId);

    @Override
    public int compare(Task left, Task right) {
        return ORDER.compare(left, right);
    }
}
