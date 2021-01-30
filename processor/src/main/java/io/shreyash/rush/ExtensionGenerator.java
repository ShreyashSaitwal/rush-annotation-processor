package io.shreyash.rush;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class ExtensionGenerator {

  private static final Map<String, List<ExtensionInfo>> externalComponentsByPackage =
      new TreeMap<>();
  private static String externalComponentsDirPath;
  private static String androidRuntimeClassDirPath;
  private static String buildServerClassDirPath;
  private static String externalComponentsTempDirPath;
  private static boolean useFQCN = false;

  /**
   * The definitions of the arguments used by this script
   * <p>
   * args[0]: the path to simple_component.json
   * args[1]: the path to simple_component_build_info.json
   * args[2]: the path to ExternalComponentAsset.dir: "${local.build.dir}/ExternalComponents"
   * args[3]: the path to "${AndroidRuntime-class.dir}"
   * args[4]: the path to dependency dir
   * args[5]: the path to external componentsTemp directory
   * args[6]: use FQCN
   */
  public static void main(String[] args) throws IOException, JSONException {
    String simple_component_json = readFile(args[0], Charset.defaultCharset());
    String simple_component_build_info_json = readFile(args[1], Charset.defaultCharset());

    externalComponentsDirPath = args[2];
    androidRuntimeClassDirPath = args[3];
    buildServerClassDirPath = args[4];
    externalComponentsTempDirPath = args[5];
    useFQCN = Boolean.parseBoolean(args[6]);

    JSONArray simpleComponentDescriptors = new JSONArray(simple_component_json);
    JSONArray simpleComponentBuildInfos = new JSONArray(simple_component_build_info_json);
    Map<String, JSONObject> buildInfos = buildInfoAsMap(simpleComponentBuildInfos);

    for (int i = 0; i < simpleComponentDescriptors.length(); i++) {
      JSONObject componentDescriptor = (JSONObject) simpleComponentDescriptors.get(i);
      if (componentDescriptor.get("external").toString().equals("true")) {
        ExtensionInfo info = new ExtensionInfo(componentDescriptor, buildInfos.get(componentDescriptor.getString("type")));
        if (!externalComponentsByPackage.containsKey(info.packageName)) {
          externalComponentsByPackage.put(info.packageName, new ArrayList<ExtensionInfo>());
        }
        externalComponentsByPackage.get(info.packageName).add(info);
      }
    }

    generateAllExtensions();
  }

  private static Map<String, JSONObject> buildInfoAsMap(JSONArray buildInfos) throws JSONException {
    Map<String, JSONObject> result = new HashMap<>();
    for (int i = 0; i < buildInfos.length(); i++) {
      JSONObject componentBuildInfo = buildInfos.getJSONObject(i);
      result.put(componentBuildInfo.getString("type"), componentBuildInfo);
    }
    return result;
  }

  private static void generateAllExtensions() throws IOException, JSONException {
    for (Map.Entry<String, List<ExtensionInfo>> entry : externalComponentsByPackage.entrySet()) {
      String name = useFQCN && entry.getValue().size() == 1 ? entry.getValue().get(0).type : entry.getKey();
      generateExternalComponentDescriptors(name, entry.getValue());
      for (ExtensionInfo info : entry.getValue()) {
        copyIcon(name, info.descriptor);
        copyLicense(name, info.descriptor);
        copyAssets(name, info.descriptor);
      }
      generateExternalComponentBuildFiles(name, entry.getValue());
      generateExternalComponentOtherFiles(name);
    }
  }

  private static void generateExternalComponentDescriptors(String packageName, List<ExtensionInfo> infos)
      throws IOException, JSONException {
    StringBuilder sb = new StringBuilder("[");
    boolean first = true;
    for (ExtensionInfo info : infos) {
      if (!first) {
        sb.append(',');
      } else {
        first = false;
      }
      sb.append(info.descriptor.toString(1));
    }
    sb.append(']');
    String components = sb.toString();
    String extensionDirPath = externalComponentsDirPath + File.separator + packageName;
    ensureDirectory(extensionDirPath, "ERR Unable to create build directory for [" + packageName + "].");
    FileWriter jsonWriter = null;
    try {
      jsonWriter = new FileWriter(extensionDirPath + File.separator + "components.json");
      jsonWriter.write(components);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (jsonWriter != null) {
        jsonWriter.close();
      }
    }
    // Write legacy format to transition developers
    try {
      jsonWriter = new FileWriter(extensionDirPath + File.separator + "component.json");
      jsonWriter.write(infos.get(0).descriptor.toString(1));
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (jsonWriter != null) {
        jsonWriter.close();
      }
    }
  }

  private static void generateExternalComponentBuildFiles(String packageName, List<ExtensionInfo> extensions) throws IOException {
    String extensionDirPath = externalComponentsDirPath + File.separator + packageName;
    String extensionTempDirPath = externalComponentsTempDirPath + File.separator + packageName;
    String extensionFileDirPath = extensionDirPath + File.separator + "files";
    copyRelatedExternalClasses(androidRuntimeClassDirPath, packageName, extensionTempDirPath);

    JSONArray buildInfos = new JSONArray();
    for (ExtensionInfo info : extensions) {
      JSONObject componentBuildInfo = info.buildInfo;
      try {
        JSONArray librariesNeeded = componentBuildInfo.getJSONArray("libraries");
        for (int j = 0; j < librariesNeeded.length(); ++j) {
          // Copy Library files for Unjar and Jaring
          String library = librariesNeeded.getString(j);
          copyFile(buildServerClassDirPath + File.separator + library,
              extensionTempDirPath + File.separator + library);
        }
        //empty the libraries meta-data to avoid redundancy
        componentBuildInfo.put("libraries", new JSONArray());
      } catch (JSONException e) {
        // bad
        throw new IllegalStateException("ERR An unexpected error occurred while parsing simple_components.json",
            e);
      }
      buildInfos.put(componentBuildInfo);
    }

    // Create component_build_info.json
    ensureDirectory(extensionFileDirPath, "ERR Unable to create path for component_build_info.json");
    FileWriter extensionBuildInfoFile = null;
    try {
      extensionBuildInfoFile = new FileWriter(extensionFileDirPath + File.separator + "component_build_infos.json");
      extensionBuildInfoFile.write(buildInfos.toString());

    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (extensionBuildInfoFile != null) {
        extensionBuildInfoFile.flush();
        extensionBuildInfoFile.close();
      }
    }
    // Write out legacy component_build_info.json to transition developers
    try {
      extensionBuildInfoFile = new FileWriter(extensionFileDirPath + File.separator + "component_build_info.json");
      extensionBuildInfoFile.write(buildInfos.get(0).toString());
    } catch (IOException | JSONException e) {
      e.printStackTrace();
    } finally {
      if (extensionBuildInfoFile != null) {
        extensionBuildInfoFile.close();
      }
    }
  }

  private static void copyIcon(String packageName, JSONObject componentDescriptor)
      throws IOException, JSONException {
    String icon = componentDescriptor.getString("iconName");
    if (icon.equals("") || icon.startsWith("http:") || icon.startsWith("https:")) {
      // Icon will be loaded from the web
      return;
    }
    String packagePath = packageName.replace('.', File.separatorChar);
    File sourceDir = new File(externalComponentsDirPath + File.separator + ".." + File.separator + ".." + File.separator + "src" + File.separator + packagePath);
    File image = Paths.get(sourceDir.getPath(), "assets", icon).toFile();
    if (image.exists()) {
      File dstIcon = new File(externalComponentsDirPath + File.separator + packageName + File.separator + icon);
      ensureDirectory(dstIcon.getParent(), "ERR Unable to copy extension icon [" + icon + "] to it's destination directory.");
      copyFile(image.getAbsolutePath(), dstIcon.getAbsolutePath());
    }
  }

  private static void copyLicense(String packageName, JSONObject componentDescriptor)
      throws IOException, JSONException {
    String license = componentDescriptor.getString("licenseName");
    if ("".equals(license) || license.startsWith("http:") || license.startsWith("https:")) {
      // License will be loaded from the web
      return;
    }
    File licenseFile = new File(license);
    if (licenseFile.exists()) {
      File destinationLicense = new File(externalComponentsDirPath + File.separator + packageName + File.separator + license);
      ensureDirectory(destinationLicense.getParent(), "ERR Unable to copy LICENSE file to it's destination directory.");
      copyFile(licenseFile.getAbsolutePath(), destinationLicense.getAbsolutePath());
    }
  }

  private static void copyAssets(String packageName, JSONObject componentDescriptor)
      throws IOException, JSONException {
    JSONArray assets = componentDescriptor.optJSONArray("assets");
    if (assets == null) {
      return;
    }

    // Get asset source directory
    String packagePath = packageName.replace('.', File.separatorChar);
    File sourceDir = new File(externalComponentsDirPath + File.separator + ".." + File.separator + ".." + File.separator + "src" + File.separator + packagePath);
    File assetSrcDir = new File(sourceDir, "assets");
    if (!assetSrcDir.exists() || !assetSrcDir.isDirectory()) {
      return;
    }

    // Get asset dest directory
    File destDir = new File(externalComponentsDirPath + File.separator + packageName + File.separator);
    File assetDestDir = new File(destDir, "assets");
    ensureFreshDirectory(assetDestDir.getPath()
    );

    // Copy assets
    for (int i = 0; i < assets.length(); i++) {
      String asset = assets.getString(i);
      if (!asset.isEmpty()) {
        if (!copyFile(assetSrcDir.getAbsolutePath() + File.separator + asset,
            assetDestDir.getAbsolutePath() + File.separator + asset)) {
          throw new IllegalStateException("ERR Unable to copy asset [" + asset + "] to destination.");
        }
      }
    }
  }

  private static void generateExternalComponentOtherFiles(String packageName) throws IOException {
    String extensionDirPath = externalComponentsDirPath + File.separator + packageName;

    // Create extension.properties
    StringBuilder extensionPropertiesString = new StringBuilder();
    extensionPropertiesString.append("type=external\n");
    FileWriter extensionPropertiesFile = new FileWriter(extensionDirPath + File.separator + "extension.properties");
    try {
      extensionPropertiesFile.write(extensionPropertiesString.toString());
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      extensionPropertiesFile.flush();
      extensionPropertiesFile.close();
    }
  }

  /**
   * Read a file and returns its content
   *
   * @param path     the path of the file to be read
   * @param encoding the encoding system
   */
  private static String readFile(String path, Charset encoding) throws IOException {
    byte[] encoded = Files.readAllBytes(Paths.get(path));
    return new String(encoded, encoding);
  }

  /**
   * Copy one file to another. If destination file does not exist, it is created.
   *
   * @param srcPath absolute path to source file
   * @param dstPath absolute path to destination file
   * @return {@code true} if the copy succeeds, {@code false} otherwise
   */
  private static Boolean copyFile(String srcPath, String dstPath) {
    try {
      FileInputStream in = new FileInputStream(srcPath);
      FileOutputStream out = new FileOutputStream(dstPath);
      byte[] buf = new byte[1024];
      int len;
      while ((len = in.read(buf)) > 0) {
        out.write(buf, 0, len);
      }
      in.close();
      out.close();
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  /**
   * Copy a compiled classes related to a given extension in his package folder
   *
   * @param srcPath          the folder in which to check compiled classes
   * @param extensionPackage the classpath of the extension
   * @param destPath         where the compiled classes will be copied
   */
  private static void copyRelatedExternalClasses(final String srcPath, String extensionPackage,
                                                 final String destPath) throws IOException {
    File srcFolder = new File(srcPath);
    File[] files = srcFolder.listFiles();
    if (files == null) {
      return;
    }
    for (File fileEntry : files) {
      if (fileEntry.isFile()) {
        if (isRelatedExternalClass(fileEntry.getAbsolutePath(), extensionPackage)) {
          copyFile(fileEntry.getAbsolutePath(), destPath + File.separator + fileEntry.getName());
        }
      } else if (fileEntry.isDirectory()) {
        String newDestPath = destPath + fileEntry.getAbsolutePath().substring(srcFolder.getAbsolutePath().length());
        ensureDirectory(newDestPath, "ERR Unable to create temporary path for extension build.");
        copyRelatedExternalClasses(fileEntry.getAbsolutePath(), extensionPackage, newDestPath);
      }
    }
  }

  /**
   * Returns true if a class is related to an external component
   * Current implementation returns true for all files in the same package as that of the external component
   * A better implementation is possible but might be more complex
   *
   * @param testClassAbsolutePath absolute path of the class file
   * @param extensionPackage      package of the external component
   * @return {@code true} if the Java class file at {@code testClassAbsolutePath} is a member of
   * {@code extensionPackage}, {@code false} otherwise
   */
  private static boolean isRelatedExternalClass(final String testClassAbsolutePath, final String extensionPackage) {
    if (!testClassAbsolutePath.endsWith(".class")) {  // Ignore things that aren't class files...
      return false;
    }
    String componentPackagePath = extensionPackage.replace(".", File.separator);

    String testClassPath = getClassPackage(testClassAbsolutePath);
    testClassPath = testClassPath.replace(".", File.separator);
    return testClassPath.startsWith(componentPackagePath);
  }

  private static String getClassPackage(String classAbsolutePath) {
    String parentPath = androidRuntimeClassDirPath;
    if (!parentPath.endsWith("/")) {
      parentPath += "/";
    }
    parentPath = parentPath.replace("/", File.separator);
    String componentPackage = classAbsolutePath.substring(classAbsolutePath.indexOf(parentPath) + parentPath.length());
    componentPackage = componentPackage.substring(0, componentPackage.lastIndexOf(File.separator));
    componentPackage = componentPackage.replace(File.separator, ".");
    return componentPackage;
  }

  private static boolean deleteRecursively(File dirOrFile) {
    if (dirOrFile.isFile()) {
      return dirOrFile.delete();
    } else {
      boolean result = true;
      File[] children = dirOrFile.listFiles();
      if (children != null) {
        for (File child : children) {
          result = result && deleteRecursively(child);
        }
      }
      return result && dirOrFile.delete();
    }
  }

  private static void ensureFreshDirectory(String path) throws IOException {
    File file = new File(path);
    if (file.exists() && !deleteRecursively(file)) {
      throw new IOException("ERR Unable to delete the assets directory for the extension.");
    }
    if (!file.mkdirs()) {
      throw new IOException("ERR Unable to delete the assets directory for the extension.");
    }
  }

  private static void ensureDirectory(String path, String errorMessage) throws IOException {
    File file = new File(path);
    if (!file.exists() && !file.mkdirs()) {
      throw new IOException(errorMessage);
    }
  }

  /**
   * Container class to store information about an extension.
   */
  private static class ExtensionInfo {
    private final String type;
    private final String packageName;
    private final JSONObject descriptor;
    private final JSONObject buildInfo;

    ExtensionInfo(JSONObject descriptor, JSONObject buildInfo) {
      this.descriptor = descriptor;
      this.buildInfo = buildInfo;
      this.type = descriptor.optString("type");
      this.packageName = type.substring(0, type.lastIndexOf('.'));
    }
  }
}
