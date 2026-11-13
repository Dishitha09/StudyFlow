package com.studyflow.strategy;

import com.studyflow.model.Task;
import java.util.Comparator;

public final class SortByPriority implements TaskSortStrategy {
    private static final Comparator<Task> ORDER = Comparator.comparing(Task::getPriority)
            .thenComparing(Comparator.naturalOrder());

    @Override
    public int compare(Task left, Task right) {
        return ORDER.compare(left, right);
    }
}
