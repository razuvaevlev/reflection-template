package org.example.generator;

import com.google.auto.service.AutoService;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

@AutoService(javax.annotation.processing.Processor.class)
public final class GeneratableProcessor extends AbstractProcessor {
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Generatable.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Generatable.class)) {
            if (element.getKind() != ElementKind.CONSTRUCTOR) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@Generatable can only be applied to a constructor", element);
                continue;
            }
            Element owner = element.getEnclosingElement();
            long markedConstructors = owner.getEnclosedElements().stream()
                    .filter(candidate -> candidate.getKind() == ElementKind.CONSTRUCTOR)
                    .filter(candidate -> candidate.getAnnotation(Generatable.class) != null)
                    .count();
            if (markedConstructors > 1) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Only one constructor may be marked with @Generatable", owner);
            }
        }
        return true;
    }
}
