package me.sodiumlegacy.mod.client.render.immediate.model;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * A minimal replacement for modern Minecraft's {@code MatrixStack}, which does not exist in the
 * 1.8.9. It keeps a stack of (position, normal) matrices so that the fast model/entity render
 * paths can build the same transform hierarchy that vanilla produces with {@code GlStateManager}
 * push/pop/translate/rotate — without touching GL state.
 *
 * <p>Entry exposes {@link #getPositionMatrix()} / {@link #getNormalMatrix()} so the encoder helpers
 * keep their modern-style call sites.</p>
 */
public final class ModelMatrices {
    private static final int DEFAULT_CAPACITY = 8;

    private Entry[] stack = new Entry[DEFAULT_CAPACITY];
    private int depth;

    public static final class Entry {
        public final Matrix4f position;
        public final Matrix3f normal;

        Entry(Matrix4f position, Matrix3f normal) {
            this.position = position;
            this.normal = normal;
        }

        public Matrix4f getPositionMatrix() {
            return this.position;
        }

        public Matrix3f getNormalMatrix() {
            return this.normal;
        }
    }

    public ModelMatrices() {
        this.enqueue();
    }

    private void enqueue() {
        if (this.depth == this.stack.length) {
            Entry[] bigger = new Entry[this.stack.length * 2];
            System.arraycopy(this.stack, 0, bigger, 0, this.stack.length);
            this.stack = bigger;
        }

        Matrix4f position = new Matrix4f();
        Matrix3f normal = new Matrix3f();
        this.stack[this.depth++] = new Entry(position, normal);
    }

    public void push() {
        Entry top = this.stack[this.depth - 1];

        Matrix4f next = new Matrix4f(top.position);
        Matrix3f nextNormal = new Matrix3f(top.normal);
        this.stack[this.depth++] = new Entry(next, nextNormal);
    }

    public void pop() {
        this.depth--;
    }

    public Entry peek() {
        return this.stack[this.depth - 1];
    }

    public int size() {
        return this.depth;
    }

    public void clear() {
        this.depth = 1;
    }

    private Matrix4f top() {
        return this.stack[this.depth - 1].position;
    }

    private void refreshNormal() {
        // Approximate the normal matrix from the position matrix: for the rigid transforms used by
        // entity models (translate/rotate only) the upper-left 3x3 block is already orthonormal.
        this.stack[this.depth - 1].normal.set(this.top());
    }

    public void translate(float x, float y, float z) {
        this.top().translate(x, y, z);
        this.refreshNormal();
    }

    public void rotateZ(float degrees) {
        this.top().rotateZ((float) Math.toRadians(degrees));
        this.refreshNormal();
    }

    public void rotateY(float degrees) {
        this.top().rotateY((float) Math.toRadians(degrees));
        this.refreshNormal();
    }

    public void rotateX(float degrees) {
        this.top().rotateX((float) Math.toRadians(degrees));
        this.refreshNormal();
    }
}
