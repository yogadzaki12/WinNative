package kr.co.iefriends.pcsx2;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Asset-to-disk copy helper used by MainActivityRuntime's first-run/update sync. */
public final class AssetFiles {
    private AssetFiles() {}

    public static void copyFile(Context p_context, String srcFile, String destFile) {
        AssetManager assetMgr = p_context.getAssets();

        InputStream is = null;
        FileOutputStream os = null;
        // Files that MUST always reflect the current APK assets, even on an app
        // UPDATE where the on-disk copy already exists (copyFile otherwise skips
        // existing files): shader sources (a C++ entry-point bump needs the new
        // GLSL or the compiler reports "main() is missing"), the GameDB
        // (GameIndex.yaml — shipped per-game fixes like True Crime's must reach
        // users who already have an old copy, or the fix never applies), AND our
        // GameDB override (armsx2_overrides.yaml — the whole point of that file is
        // shipping per-game fixes like VP2/LEGO Batman; without force-refresh an
        // existing user keeps the original copy and override edits never land).
        final boolean forceFresh = srcFile.contains("shaders")
                || srcFile.endsWith("GameIndex.yaml")
                || srcFile.endsWith("armsx2_overrides.yaml");
        try {
            is = assetMgr.open(srcFile);
            File destFileObj = new File(destFile);
            boolean _exists = destFileObj.exists();
            if (forceFresh) {
                // Delete first so a partial/denied write can't silently fall back
                // to stale content.
                if (_exists && !destFileObj.delete()) {
                    Log.w("ARMSX2", "copyFile: failed to delete stale asset " + destFile);
                }
                _exists = false;
            }
            if (!_exists) {
                os = new FileOutputStream(destFile);

                byte[] buffer = new byte[1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                is.close();
                os.flush();
                os.close();
            }
        } catch (IOException e) {
            Log.e("ARMSX2", "copyFile failed: " + srcFile + " -> " + destFile + ": " + e.getMessage());
            try { if (is != null) is.close(); } catch (IOException ignored) {}
            try { if (os != null) os.close(); } catch (IOException ignored) {}
        }
    }
}
