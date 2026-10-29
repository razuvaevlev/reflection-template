package org.example.generator;

import org.example.classes.BinaryTreeNode;
import org.example.classes.Rectangle;
import org.example.classes.Shape;
import org.example.classes.Triangle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorTest {
    private final Generator generator = new Generator();

    @Test 
    void generatesPrimitiveWrappersAndString() throws Exception {
        assertInstanceOf(Integer.class, generator.generateValueOfType(int.class));
        assertInstanceOf(Double.class, generator.generateValueOfType(Double.class));
        assertInstanceOf(Boolean.class, generator.generateValueOfType(boolean.class));
        assertInstanceOf(Character.class, generator.generateValueOfType(char.class));
        assertFalse(((String) generator.generateValueOfType(String.class)).isEmpty());
    }

    @Test 
    void generatesEnumsArraysAndCollections() throws Exception {
        assertNotNull(generator.generateValueOfType(TestColor.class));
        assertTrue(((int[]) generator.generateValueOfType(int[].class)).length > 0);
        assertTrue(((String[]) generator.generateValueOfType(String[].class)).length > 0);
        assertFalse(((List<?>) generator.generateValueOfType(List.class)).isEmpty());
        assertFalse(((Map<?, ?>) generator.generateValueOfType(Map.class)).isEmpty());
    }

    @Test 
    void generatesTemplateClassesAndShapeImplementations() throws Exception {
        assertInstanceOf(Rectangle.class, generator.generateValueOfType(Rectangle.class));
        assertInstanceOf(Triangle.class, generator.generateValueOfType(Triangle.class));
        assertInstanceOf(Shape.class, generator.generateValueOfType(Shape.class));
        BinaryTreeNode node = (BinaryTreeNode) generator.generateValueOfType(BinaryTreeNode.class);
        assertNotNull(node.getData());
        assertNull(node.getLeft());
        assertNull(node.getRight());
    }

    @Test 
    void markedConstructorHasPriorityAndReceivesGeneratedValues() throws Exception {
        PreferredConstructor value = (PreferredConstructor) generator.generateValueOfType(PreferredConstructor.class);
        assertEquals("marked", value.choice);
        assertNotNull(value.value);
    }

    @Test
    void generatesCollectionElementsUsingConstructorGenericType() throws Exception {
        GenericConstructor value = (GenericConstructor) generator.generateValueOfType(GenericConstructor.class);

        assertFalse(value.values.isEmpty());
        assertTrue(value.values.stream().allMatch(Integer.class::isInstance));
    }

    @Test 
    void unsupportedRequestsFailClearly() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> generator.generateValueOfType(Void.class));
        assertTrue(error.getMessage().contains("Cannot generate"));
    }

    enum TestColor { RED, GREEN }

    static class PreferredConstructor {
        final String choice;
        final Integer value;
        PreferredConstructor(String choice) { this.choice = choice; this.value = null; }
        @Generatable PreferredConstructor(Integer value) { this.choice = "marked"; this.value = value; }
    }

    static class GenericConstructor {
        final List<Integer> values;

        GenericConstructor(List<Integer> values) {
            this.values = values;
        }
    }
}
