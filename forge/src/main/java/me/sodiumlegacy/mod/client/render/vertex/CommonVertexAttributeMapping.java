package me.sodiumlegacy.mod.client.render.vertex;

import me.sodiumlegacy.mod.api.vertex.attributes.CommonVertexAttribute;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/**
 * Platform side of {@link CommonVertexAttribute}: maps each generic attribute to the Minecraft
 * {@link VertexFormatElement} that represents it.
 *
 * <p>1.8.9 port: {@code VertexFormatElement} and {@code VertexFormats} exist in legacy yarn with the same names.
 * 1.8.9 has no "overlay" or "light" vertex elements, so those two attributes have no element (null).
 *
 * <p>The byte lengths live in {@link CommonVertexAttribute#getByteLength()} (so that code in {@code :core} can use
 * them). Since a wrong length would silently corrupt vertex data, they are verified here against the real
 * Minecraft elements the first time this class is loaded.
 */
public final class CommonVertexAttributeMapping {
    private static final VertexFormatElement[] ELEMENTS = new VertexFormatElement[CommonVertexAttribute.COUNT];

    static {
        ELEMENTS[CommonVertexAttribute.POSITION.ordinal()] = DefaultVertexFormats.POSITION_3F;
        ELEMENTS[CommonVertexAttribute.COLOR.ordinal()] = DefaultVertexFormats.COLOR_4UB;
        ELEMENTS[CommonVertexAttribute.TEXTURE.ordinal()] = DefaultVertexFormats.TEX_2F;
        // 1.8.9 port: no overlay/light element in 1.8.9; kept as null (4-byte int attributes, cf. OverlayAttribute/LightAttribute)
        ELEMENTS[CommonVertexAttribute.OVERLAY.ordinal()] = null;
        ELEMENTS[CommonVertexAttribute.LIGHT.ordinal()] = null;
        ELEMENTS[CommonVertexAttribute.NORMAL.ordinal()] = DefaultVertexFormats.NORMAL_3B;

        for (CommonVertexAttribute attribute : CommonVertexAttribute.values()) {
            VertexFormatElement element = ELEMENTS[attribute.ordinal()];

            if (element != null && element.getSize() != attribute.getByteLength()) {
                throw new IllegalStateException("CommonVertexAttribute." + attribute + " declares " +
                        attribute.getByteLength() + " bytes but the Minecraft element has " + element.getSize());
            }
        }
    }

    private CommonVertexAttributeMapping() {
    }

    public static CommonVertexAttribute getCommonType(VertexFormatElement element) {
        for (CommonVertexAttribute type : CommonVertexAttribute.values()) {
            if (ELEMENTS[type.ordinal()] == element) {
                return type;
            }
        }

        return null;
    }
}

