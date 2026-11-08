package me.sodiumlegacy.mod.client.render.immediate.model;

import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;

import java.util.List;

/**
 * 1.8.9 adaptation of the modern {@code ModelPartData} mixin interface: vanilla's
 * {@link ModelRenderer} exposes every datum as a plain public field, so no mixin is needed —
 * this thin adapter just wraps one (part is effectively read-only at render time).
 */
public final class ModelPartData {
    private final ModelRenderer part;

    private ModelPartData(ModelRenderer part) {
        this.part = part;
    }

    public static ModelPartData from(ModelRenderer part) {
        return new ModelPartData(part);
    }

    public ModelRenderer getModelPart() {
        return this.part;
    }

    public List<ModelBox> getCuboids() {
        return this.part.cubeList;
    }

    public List<ModelRenderer> getChildren() {
        return this.part.childModels;
    }

    public boolean isVisible() {
        return this.part.showModel;
    }

    public boolean isHidden() {
        return this.part.isHidden;
    }
}
