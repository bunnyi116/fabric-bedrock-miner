package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.data.Color4f;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Colors;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class TaskRender {

    public static RenderPipeline NO_DEPTH_TEST_LINES = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation("pipeline/debug_filled_box")
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_STRIP)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());


    public static RenderLayer NO_DEPTH_TEST_DEBUG_FILLED_BOX = RenderLayer.of("debug_filled_box",
            1536,
            false,
            true,
            NO_DEPTH_TEST_LINES,
            RenderLayer.MultiPhaseParameters.builder()
                    .layering(RenderLayer.VIEW_OFFSET_Z_LAYERING)
                    .build(false));

    public static void render(MatrixStack matrixStack, VertexConsumerProvider.Immediate immediate, Camera camera) {
        if (TaskManager.INSTANCE.isWorking()) {
            if (!camera.isReady()) {
                return;
            }
            var vec3d = camera.getPos().negate();
            var vertexConsumer = immediate.getBuffer(NO_DEPTH_TEST_DEBUG_FILLED_BOX);

            final var task = TaskManager.INSTANCE.getCurrentTask();
            if (task != null) {
                final var box = Box.from(Vec3d.of(task.pos)).offset(vec3d);
                drawFilledBox(matrixStack, vertexConsumer, box, Color4f.fromARGB(Colors.GREEN).withAlpha(0.2f));
            }

            for (final var item : TaskManager.INSTANCE.getPendingTasks()) {
                final var box = Box.from(Vec3d.of(item.pos)).offset(vec3d);
                if (item.equals(task)) {
                    continue;
                }
                drawFilledBox(matrixStack, vertexConsumer, box, Color4f.fromARGB(Colors.WHITE).withAlpha(0.2f));
            }
            for (final var item : Config.INSTANCE.ranges) {
                final var box = Box.enclosing(item.pos1, item.pos2).offset(vec3d);
                drawFilledBox(matrixStack, vertexConsumer, box, Color4f.WHITE.withAlpha(0.2f));
            }
        }
    }

    private static void drawFilledBox(MatrixStack matrices, VertexConsumer vertexConsumers, Box box, Color4f color4f) {
        VertexRendering.drawFilledBox(matrices, vertexConsumers, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color4f.getRed(), color4f.getGreen(), color4f.getBlue(), color4f.getAlpha());
    }

}
