package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.task.block.TaskTargetBlock;
import com.github.bunnyi116.bedrockminer.task.block.scheme.TaskSchemeBaseBlock;
import com.github.bunnyi116.bedrockminer.task.block.scheme.TaskSchemeLeverBlock;
import com.github.bunnyi116.bedrockminer.task.block.scheme.TaskSchemePistonBlock;
import com.github.bunnyi116.bedrockminer.task.block.scheme.TaskSchemeRedstoneTorchBlock;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class TaskSolution {
    private final TaskTargetBlock target;
    private final TaskMode mode;
    private final @Nullable RedstoneTorchTaskScheme[] redstoneTorchSchemes;
    private final @Nullable LeverTaskScheme[] leverSchemes;

    public TaskSolution(TaskTargetBlock target, TaskMode mode) {
        this.target = target;
        this.mode = mode;
        switch (this.mode) {
            case REDSTONE_TORCH:
                this.redstoneTorchSchemes = this.findRedstoneTorch();
                this.leverSchemes = null;
                break;
            case LEVER:
                this.redstoneTorchSchemes = null;
                this.leverSchemes = this.findLever();
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + this.mode);
        }
    }

    private RedstoneTorchTaskScheme[] findRedstoneTorch() {
        var taskSchemes = new ArrayList<RedstoneTorchTaskScheme>();
        var pistons = TaskSolutionUtils.findPistons(this.target);
        for (var piston : pistons) {
            var redstoneTorchs = TaskSolutionUtils.findRedstoneTorchs(this.target, piston);
            for (var redstoneTorch : redstoneTorchs) {
                var baseBlock = TaskSolutionUtils.findRedstoneTorchBaseBlock(redstoneTorch);
                taskSchemes.add(new RedstoneTorchTaskScheme(piston, redstoneTorch, baseBlock));
            }
        }
        return taskSchemes.toArray(new RedstoneTorchTaskScheme[0]);
    }

    private LeverTaskScheme[] findLever() {
        var taskSchemes = new ArrayList<LeverTaskScheme>();
        var pistons = TaskSolutionUtils.findPistons(this.target);
        for (var piston : pistons) {
            var levers = TaskSolutionUtils.findLevers(this.target, piston);
            for (var lever : levers) {
                var baseBlock = TaskSolutionUtils.findLeverBaseBlock(lever);
                taskSchemes.add(new LeverTaskScheme(piston, lever, baseBlock));
            }
        }
        return taskSchemes.toArray(new LeverTaskScheme[0]);
    }


    public record RedstoneTorchTaskScheme(TaskSchemePistonBlock piston, TaskSchemeRedstoneTorchBlock redstoneTorch,
                                          TaskSchemeBaseBlock baseBlock) {
    }

    public record LeverTaskScheme(TaskSchemePistonBlock piston, TaskSchemeLeverBlock lever,
                                  TaskSchemeBaseBlock baseBlock) {
    }
}
