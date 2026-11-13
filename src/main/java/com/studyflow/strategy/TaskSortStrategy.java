package com.studyflow.strategy;

import com.studyflow.model.Task;
import java.util.Comparator;

/** An interchangeable ordering for task query results. */
@FunctionalInterface
public interface TaskSortStrategy extends Comparator<Task> {
}
