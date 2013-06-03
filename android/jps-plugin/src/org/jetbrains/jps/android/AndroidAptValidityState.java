package org.jetbrains.jps.android;

import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import gnu.trove.TObjectLongHashMap;
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.android.util.ResourceFileData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.storage.ValidityState;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAptValidityState implements ValidityState {
  private static final int VERSION = 0;

  private final Map<String, ResourceFileData> myResources;
  private final TObjectLongHashMap<String> myValueResourceFilesTimestamps;
  private final List<ResourceEntry> myManifestElements;
  private final String myPackageName;
  private final Set<String> myLibPackages;
  private final String myProguardOutputCfgFile;

  public AndroidAptValidityState(@NotNull Map<String, ResourceFileData> resources,
                                 @NotNull TObjectLongHashMap<String> valueResourceFilesTimestamps,
                                 @NotNull List<ResourceEntry> manifestElements,
                                 @NotNull Collection<String> libPackages,
                                 @NotNull String packageName,
                                 @Nullable String proguardOutputCfgFile) {
    myResources = resources;
    myValueResourceFilesTimestamps = valueResourceFilesTimestamps;
    myManifestElements = manifestElements;
    myLibPackages = new HashSet<String>(libPackages);
    myPackageName = packageName;
    myProguardOutputCfgFile = proguardOutputCfgFile != null ? proguardOutputCfgFile : "";
  }

  public AndroidAptValidityState(@NotNull DataInput in) throws IOException {
    final int version = in.readInt();

    if (version != VERSION) {
      throw new IOException("old version");
    }
    myPackageName = in.readUTF();

    final int filesCount = in.readInt();
    myResources = new HashMap<String, ResourceFileData>(filesCount);

    for (int i = 0; i < filesCount; i++) {
      final String filePath = in.readUTF();

      final int entriesCount = in.readInt();
      final List<ResourceEntry> entries = new ArrayList<ResourceEntry>(entriesCount);

      for (int j = 0; j < entriesCount; j++) {
        final String resType = in.readUTF();
        final String resName = in.readUTF();
        final String resContext = in.readUTF();
        entries.add(new ResourceEntry(resType, resName, resContext));
      }
      final long timestamp = in.readLong();
      myResources.put(filePath, new ResourceFileData(entries, timestamp));
    }

    final int manifestElementCount = in.readInt();
    myManifestElements = new ArrayList<ResourceEntry>(manifestElementCount);

    for (int i = 0; i < manifestElementCount; i++) {
      final String elementType = in.readUTF();
      final String elementName = in.readUTF();
      final String elementContext = in.readUTF();
      myManifestElements.add(new ResourceEntry(elementType, elementName, elementContext));
    }

    final int libPackageCount = in.readInt();
    myLibPackages = new HashSet<String>(libPackageCount);

    for (int i = 0; i < libPackageCount; i++) {
      final String libPackage = in.readUTF();
      myLibPackages.add(libPackage);
    }

    myProguardOutputCfgFile = in.readUTF();

    final int valueResourceFilesCount = in.readInt();
    myValueResourceFilesTimestamps = new TObjectLongHashMap<String>(valueResourceFilesCount);

    for (int i = 0; i < valueResourceFilesCount; i++) {
      final String filePath = in.readUTF();
      final long timestamp = in.readLong();
      myValueResourceFilesTimestamps.put(filePath, timestamp);
    }
  }

  @Override
  public boolean equalsTo(ValidityState otherState) {
    if (!(otherState instanceof AndroidAptValidityState)) {
      return false;
    }
    // we do not compare myValueResourceFilesTimestamps maps here, because we don't run apt if some value resource xml files were changed,
    // but whole set of value resources was not. These maps are checked by AndroidSourceGeneratingBuilder for optimization only
    final AndroidAptValidityState otherAndroidState = (AndroidAptValidityState)otherState;
    return otherAndroidState.myPackageName.equals(myPackageName) &&
           otherAndroidState.myResources.equals(myResources) &&
           otherAndroidState.myManifestElements.equals(myManifestElements) &&
           otherAndroidState.myLibPackages.equals(myLibPackages) &&
           otherAndroidState.myProguardOutputCfgFile.equals(myProguardOutputCfgFile);
  }

  @Override
  public void save(DataOutput out) throws IOException {
    out.writeInt(VERSION);
    out.writeUTF(myPackageName);
    out.writeInt(myResources.size());

    for (Map.Entry<String, ResourceFileData> entry : myResources.entrySet()) {
      out.writeUTF(entry.getKey());

      final ResourceFileData fileData = entry.getValue();
      final List<ResourceEntry> resources = fileData.getValueResources();
      out.writeInt(resources.size());

      for (ResourceEntry resource : resources) {
        out.writeUTF(resource.getType());
        out.writeUTF(resource.getName());
        out.writeUTF(resource.getContext());
      }
      out.writeLong(fileData.getTimestamp());
    }
    out.writeInt(myManifestElements.size());

    for (ResourceEntry manifestElement : myManifestElements) {
      out.writeUTF(manifestElement.getType());
      out.writeUTF(manifestElement.getName());
      out.writeUTF(manifestElement.getContext());
    }
    out.writeInt(myLibPackages.size());

    for (String libPackage : myLibPackages) {
      out.writeUTF(libPackage);
    }
    out.writeUTF(myProguardOutputCfgFile);

    out.writeInt(myValueResourceFilesTimestamps.size());

    for (Object key : myValueResourceFilesTimestamps.keys()) {
      final String strKey = (String)key;
      out.writeUTF(strKey);
      out.writeLong(myValueResourceFilesTimestamps.get(strKey));
    }
  }

  public Map<String, ResourceFileData> getResources() {
    return myResources;
  }

  public TObjectLongHashMap<String> getValueResourceFilesTimestamps() {
    return myValueResourceFilesTimestamps;
  }
}
