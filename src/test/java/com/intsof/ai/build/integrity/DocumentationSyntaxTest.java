package com.intsof.ai.build.integrity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Documentation Syntax Checks")
class DocumentationSyntaxTest {

  @Test
  @DisplayName("README.md XML configurations should be structurally valid for Plugin Mojos")
  void testReadmeXmlBlocks() throws Exception {
    validateMarkdownFile(Paths.get("README.md"));
  }

  @Test
  @DisplayName("CONTRIBUTING.md XML configurations should be structurally valid for Plugin Mojos")
  void testContributingXmlBlocks() throws Exception {
    validateMarkdownFile(Paths.get("CONTRIBUTING.md"));
  }

  @Test
  @DisplayName("usage.md XML configurations should be structurally valid for Plugin Mojos")
  void testUsageXmlBlocks() throws Exception {
    validateMarkdownFile(Paths.get("src", "site", "markdown", "usage.md"));
  }

  @Test
  @DisplayName("faq.md XML configurations should be structurally valid for Plugin Mojos")
  void testFaqlXmlBlocks() throws Exception {
    validateMarkdownFile(Paths.get("src", "site", "markdown", "faq.md"));
  }

  private void validateMarkdownFile(Path markdownFile) throws IOException {
    if (!Files.exists(markdownFile)) {
      System.out.println("Skipping missing file: " + markdownFile);
      return;
    }
    List<String> lines = Files.readAllLines(markdownFile);
    boolean inXmlBlock = false;
    StringBuilder currentBlock = new StringBuilder();
    int blockCount = 0;

    for (String line : lines) {
      if (line.trim().startsWith("```xml")) {
        inXmlBlock = true;
        currentBlock.setLength(0);
        blockCount++;
      } else if (inXmlBlock && line.trim().startsWith("```")) {
        inXmlBlock = false;
        String xmlContent = currentBlock.toString().trim();
        final int currentBlockId = blockCount;
        assertDoesNotThrow(
            () ->
                validateXmlSnippet(
                    xmlContent, markdownFile.getFileName() + " block " + currentBlockId),
            "XML block failed validation in " + markdownFile.getFileName());
      } else if (inXmlBlock) {
        currentBlock.append(line).append("\n");
      }
    }
  }

  private void validateXmlSnippet(String xml, String context) throws Exception {
    // Some snippets are partial (e.g. just <configuration> or <plugin>). Wrap them to ensure
    // well-formedness.
    String wrappedXml = "<wrapper>" + xml + "</wrapper>";

    Xpp3Dom dom;
    try {
      dom = Xpp3DomBuilder.build(new StringReader(wrappedXml));
    } catch (XmlPullParserException | IOException e) {
      fail("Failed to parse XML in " + context + ":\n" + xml + "\nError: " + e.getMessage());
      return;
    }

    // Find <configuration> blocks anywhere in the snippet
    List<Xpp3Dom> configs = findNodesByName(dom, "configuration");
    if (configs.isEmpty()) {
      return; // No plugin configuration to validate
    }

    for (Xpp3Dom config : configs) {
      validateConfigurationAgainstMojo(config, context);
    }
  }

  private List<Xpp3Dom> findNodesByName(Xpp3Dom root, String targetName) {
    List<Xpp3Dom> matches = new ArrayList<>();
    if (root.getName().equals(targetName)) {
      matches.add(root);
    }
    for (Xpp3Dom child : root.getChildren()) {
      matches.addAll(findNodesByName(child, targetName));
    }
    return matches;
  }

  private void validateConfigurationAgainstMojo(Xpp3Dom configDom, String context)
      throws Exception {
    // We validate against a representative Mojo (HashVerifyMojo covers most fields)
    Class<?> targetClass = HashVerifyMojo.class;

    for (Xpp3Dom fieldDom : configDom.getChildren()) {
      String paramName = fieldDom.getName();
      Field parameterField = getParameterField(targetClass, paramName);

      if (parameterField == null) {
        // Unrecognized parameter - skip (might be a maven core parameter like <executionRootOnly>)
        continue;
      }

      Class<?> fieldType = parameterField.getType();

      // Check for the "Basic element must not contain child elements" Plexus error
      if (isBasicType(fieldType)) {
        if (fieldDom.getChildCount() > 0) {
          fail(
              "Configuration error in "
                  + context
                  + ": Basic element '"
                  + paramName
                  + "' must not contain child elements! Documented XML:\n"
                  + fieldDom.toString());
        }
      }
    }
  }

  private Field getParameterField(Class<?> clazz, String fieldName) {
    Class<?> current = clazz;
    while (current != null) {
      try {
        Field f = current.getDeclaredField(fieldName);
        if (f.isAnnotationPresent(Parameter.class)) {
          return f;
        }
      } catch (NoSuchFieldException e) {
        // Check superclass
      }
      current = current.getSuperclass();
    }
    return null;
  }

  private boolean isBasicType(Class<?> type) {
    return type.isPrimitive()
        || type == String.class
        || type == Boolean.class
        || type == Integer.class
        || type.isEnum();
  }
}
