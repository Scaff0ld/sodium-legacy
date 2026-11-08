package me.sodiumlegacy.mod.client.render.vertex;

import me.sodiumlegacy.mod.api.vertex.attributes.CommonVertexAttribute;
import me.sodiumlegacy.mod.api.vertex.format.VertexFormatDescription;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

public class VertexFormatDescriptionImpl implements VertexFormatDescription {
    private final int id;
    private final int stride;

    private final int[] offsets;

    public VertexFormatDescriptionImpl(VertexFormat format, int id) {
        this.id = id;
        this.stride = format.getVertexSize();

        this.offsets = getOffsets(format);
    }

    public static int[] getOffsets(VertexFormat format) {
        final int[] commonElementOffsets = new int[CommonVertexAttribute.COUNT];

        Arrays.fill(commonElementOffsets, -1);

        // 1.8.9 VertexFormat: getElements() returns the element list and getIndex(i) returns the
        // byte offset of element i (the modern accessor's IntList offset field does not exist).
        List<VertexFormatElement> elementList = format.getElements();

        for (int elementIndex = 0; elementIndex < elementList.size(); elementIndex++) {
            VertexFormatElement element = elementList.get(elementIndex);
            CommonVertexAttribute commonType = CommonVertexAttributeMapping.getCommonType(element);

            if (commonType != null) {
                commonElementOffsets[commonType.ordinal()] = format.getIndex(elementIndex);
            }
        }

        return commonElementOffsets;
    }

    @Override
    public boolean containsElement(CommonVertexAttribute element) {
        return this.offsets[element.ordinal()] != -1;
    }

    @Override
    public int getElementOffset(CommonVertexAttribute element) {
        int offset = this.offsets[element.ordinal()];

        if (offset == -1) {
            throw new NoSuchElementException("Vertex format does not contain element: " + element);
        }

        return offset;
    }

    @Override
    public int id() {
        return this.id;
    }

    @Override
    public int stride() {
        return this.stride;
    }
}
