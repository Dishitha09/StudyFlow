package com.studyflow.strategy;

import com.studyflow.model.Task;

public final class SortByDeadline implements TaskSortStrategy {
    @Override
    public int compare(Task left, Task right) {
        return left.compareTo(right);
    }
}
