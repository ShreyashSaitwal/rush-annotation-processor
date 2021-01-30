package io.shreyash.rush.blocks;

import com.google.appinventor.components.annotations.DesignerProperty;
import io.shreyash.rush.util.CheckName;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.tools.Diagnostic;

public class Property {
  private final Element element;
  private final ExtensionFieldInfo ext;
  private final Messager messager;
  private String name;
  private String defaultVal;
  private String editorType;
  private String[] args;
  private boolean alwaysSend;

  public Property(Element element, ExtensionFieldInfo ext, Messager messager) {
    this.element = element;
    this.ext = ext;
    this.messager = messager;
  }

  public Property build() {
    if (!CheckName.isPascalCase(element)) {
      messager.printMessage(Diagnostic.Kind.WARNING, "Designer property '" + element.getSimpleName() + "' should follow PascalCase naming convention.");
    }
    ExecutableElement executableElement = (ExecutableElement) element;
    name = executableElement.getSimpleName().toString();

    if (!ext.getBlockProps().containsKey(name)) {
      messager.printMessage(Diagnostic.Kind.ERROR, "Unable to find corresponding @SimpleProperty annotation for designer property '" + name + "'.");
    } else {
      defaultVal = executableElement.getAnnotation(DesignerProperty.class).defaultValue();
      editorType = executableElement.getAnnotation(DesignerProperty.class).defaultValue();
      args = executableElement.getAnnotation(DesignerProperty.class).editorArgs();
      alwaysSend = executableElement.getAnnotation(DesignerProperty.class).alwaysSend();

      if (!defaultVal.equals("")) {
        ext.getBlockProps().get(name).setDefaultVal(defaultVal);
      }

      if (alwaysSend) {
        ext.getBlockProps().get(name).setAlwaysSend(true);
      }
    }

    return this;
  }

  public String getName() {
    return name;
  }

  public String getDefaultVal() {
    return defaultVal;
  }

  public String getEditorType() {
    return editorType;
  }

  public String[] getArgs() {
    return args;
  }

  public boolean isAlwaysSend() {
    return alwaysSend;
  }
}
