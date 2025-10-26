package com.github.bunnyi116.bedrockminer.mixin;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.task.TaskRender;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = WorldRenderer.class, priority = 999)
public abstract class MixinWorldRenderer {

    @Shadow
    @Final
    private BufferBuilderStorage bufferBuilders;
    @Shadow
    @Final
    private DefaultFramebufferSet framebufferSet;

    @Inject(method = "render", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/FrameGraphBuilder;run(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/FrameGraphBuilder$Profiler;)V"),
            locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void render(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f matrix4f, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci, float f, Profiler profiler, Vec3d vec3d, Frustum frustum, Matrix4fStack matrix4fStack, FrameGraphBuilder frameGraphBuilder) {
        final var framePass = frameGraphBuilder.createPass(BedrockMiner.MOD_ID);
        framebufferSet.mainFramebuffer = framePass.transfer(framebufferSet.mainFramebuffer);
        framePass.setRenderer(() -> {
            RenderSystem.setShaderFog(fogBuffer);
            MatrixStack matrixStack = new MatrixStack();
            VertexConsumerProvider.Immediate immediate = bufferBuilders.getEntityVertexConsumers();
            TaskRender.render(matrixStack, immediate, camera);
            immediate.drawCurrentLayer();
        });
    }
}

