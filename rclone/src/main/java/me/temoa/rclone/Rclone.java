package me.temoa.rclone;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import ca.pkay.rcloneexplorer.AboutResult;
import ca.pkay.rcloneexplorer.Items.FileItem;
import ca.pkay.rcloneexplorer.Items.RemoteItem;

/**
 * @noinspection unused
 */
public class Rclone {

    private static final String TAG = "Rclone";

    public static final String RCLONE_CONFIG_NAME_KEY = "rclone_remote_name";

    private Context context;
    private String rclone;
    private String rcloneConf;

    public Rclone(Context context) {
        this.context = context;
        this.rclone = context.getApplicationInfo().nativeLibraryDir + "/librclone.so";
        this.rcloneConf = context.getFilesDir().getPath() + "/rclone.conf";
    }

    public boolean hasRCloneConf() {
        final File file = new File(rcloneConf);
        return file.exists();
    }

    public boolean deleteRCloneConf() {
        final File file = new File(rcloneConf);
        if (file.exists()) {
            return file.delete();
        } else {
            return true;
        }
    }

    private String[] createCommand(ArrayList<String> args) {
        String[] command = new String[args.size()];
        for (int i = 0; i < args.size(); i++) {
            command[i] = args.get(i);
        }
        return command;
    }

    private String[] createCommand(String... args) {
        boolean loggingEnabled = PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.pref_key_logs), false);
        ArrayList<String> command = new ArrayList<>();

        command.add(rclone);
        command.add("--config");
        command.add(rcloneConf);

        if (loggingEnabled) {
            command.add("-vvv");
        }

        command.addAll(Arrays.asList(args));
        return createCommand(command);
    }

    private String[] createCommandWithOptions(String... args) {
        ArrayList<String> arguments = new ArrayList<String>(Arrays.asList(args));
        return createCommandWithOptions(arguments);
    }

    private String[] createCommandWithOptions(ArrayList<String> args) {
        boolean loggingEnabled = PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.pref_key_logs), false);
        ArrayList<String> command = new ArrayList<>();

        String cachePath = context.getCacheDir().getAbsolutePath();

        command.add(rclone);
        command.add("--cache-chunk-path");
        command.add(cachePath);
        command.add("--cache-db-path");
        command.add(cachePath);

        /*

        This fixed some bug. I dont know which one, but it breaks transfer of big files where
        the checksum needs to be calculated.
        This was probably due to some timeout for connecting misconfigured remotes.

        command.add("--low-level-retries");
        command.add("2");

        command.add("--timeout");
        command.add("5s");
        command.add("--contimeout");
        command.add("5s");
        */

        command.add("--config");
        command.add(rcloneConf);

        if (loggingEnabled) {
            command.add("-vvv");
        }

        command.addAll(args);
        return createCommand(command);
    }

    public String[] getRcloneEnv(String... overwriteOptions) {
        ArrayList<String> environmentValues = new ArrayList<>();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);

        boolean proxyEnabled = pref.getBoolean(context.getString(R.string.pref_key_use_proxy), false);
        if (proxyEnabled) {
            String noProxy = pref.getString(context.getString(R.string.pref_key_no_proxy_hosts), "localhost");
            String protocol = pref.getString(context.getString(R.string.pref_key_proxy_protocol), "http");
            String host = pref.getString(context.getString(R.string.pref_key_proxy_host), "localhost");
            String user = pref.getString(context.getString(R.string.pref_key_proxy_username), "");
            String pass = pref.getString(context.getString(R.string.pref_key_proxy_password), "");
            int port = pref.getInt(context.getString(R.string.pref_key_proxy_port), 8080);
            String auth = "";
            if (!(user + pass).isEmpty()) {
                auth = user + ":" + pass + "@";
            }
            String url = protocol + "://" + auth + host + ":" + port;
            // per https://golang.org/pkg/net/http/#ProxyFromEnvironment
            environmentValues.add("http_proxy=" + url);
            environmentValues.add("https_proxy=" + url);
            environmentValues.add("no_proxy=" + noProxy);
        }

        // if TMPDIR is not set, golang uses /data/local/tmp which is only
        // only accessible for the shell user
        String tmpDir = context.getCacheDir().getAbsolutePath();
        environmentValues.add("TMPDIR=" + tmpDir);

        // ignore chtimes errors
        // ref: https://github.com/rclone/rclone/issues/2446
        environmentValues.add("RCLONE_LOCAL_NO_SET_MODTIME=true");

        // Allow the caller to overwrite any option for special cases
        Iterator<String> envVarIter = environmentValues.iterator();
        while (envVarIter.hasNext()) {
            String envVar = envVarIter.next();
            String optionName = envVar.substring(0, envVar.indexOf('='));
            for (String overwrite : overwriteOptions) {
                if (overwrite.startsWith(optionName)) {
                    envVarIter.remove();
                    environmentValues.add(overwrite);
                }
            }
        }
        return environmentValues.toArray(new String[0]);
    }

    private Process getRuntimeProcess(String[] command) throws IOException {
        return getRuntimeProcess(command, new String[0]);
    }

    private Process getRuntimeProcess(String[] command, String[] env) throws IOException {
        try {
            Runtime.getRuntime().exec(rclone);
        } catch (IOException e) {
            Log.e("rclone", "Error executing rclone!" + e.getMessage());
            throw new IOException("Error executing rclone!" + e.getMessage());
        }
        return Runtime.getRuntime().exec(command, env);
    }

    @Nullable
    public List<FileItem> getDirectoryContent(RemoteItem remote, String path, boolean startAtRoot) {
        String remoteAndPath = remote.getName() + ":";
        if (startAtRoot) {
            remoteAndPath += "/";
        }
        if (path.compareTo("//" + remote.getName()) != 0) {
            remoteAndPath += path;
        }

        String[] command;
        if (remote.isRemoteType(RemoteItem.LOCAL) || remote.isPathAlias()) {
            // ignore .android_secure errors
            // ref: https://github.com/rclone/rclone/issues/3179
            command = createCommandWithOptions("--ignore-errors", "lsjson", remoteAndPath);
        } else {
            command = createCommandWithOptions("lsjson", remoteAndPath);
        }
        String[] env = getRcloneEnv();
        JSONArray results;
        Process process;
        try {
            Log.d(TAG, "getDirectoryContent[ENV]: " + Arrays.toString(env));
            process = getRuntimeProcess(command, env);

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            process.waitFor();
            // For local/alias remotes, exit(6) is not a fatal error.
            if (process.exitValue() != 0 && (process.exitValue() != 6 || !remote.isRemoteType(RemoteItem.LOCAL, RemoteItem.ALIAS))) {
                return null;
            }

            String outputStr = output.toString();
            results = new JSONArray(outputStr);

        } catch (InterruptedException e) {
            Log.d(TAG, "getDirectoryContent: Aborted refreshing folder");
            return null;
        } catch (IOException | JSONException e) {
            Log.e(TAG, "getDirectoryContent: Could not get folder content", e);
            return null;
        }

        List<FileItem> fileItemList = new ArrayList<>();
        for (int i = 0; i < results.length(); i++) {
            try {
                JSONObject jsonObject = results.getJSONObject(i);
                String filePath = (path.compareTo("//" + remote.getName()) == 0) ? "" : path + "/";
                filePath += jsonObject.getString("Path");
                String fileName = jsonObject.getString("Name");
                long fileSize = jsonObject.getLong("Size");
                String fileModTime = jsonObject.getString("ModTime");
                boolean fileIsDir = jsonObject.getBoolean("IsDir");
                String mimeType = jsonObject.getString("MimeType");

                if (remote.isCrypt()) {
                    String extension = fileName.substring(fileName.lastIndexOf(".") + 1);
                    String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                    if (type != null) {
                        mimeType = type;
                    }
                }

                FileItem fileItem = new FileItem(remote, filePath, fileName, fileSize, fileModTime, mimeType, fileIsDir, startAtRoot);
                fileItemList.add(fileItem);
            } catch (JSONException e) {
                Log.e(TAG, "getDirectoryContent: Could not decode JSON", e);
                return null;
            }
        }
        return fileItemList;
    }

    public List<RemoteItem> getRemotes() {
        String[] command = createCommand("config", "dump");
        StringBuilder output = new StringBuilder();
        Process process;
        JSONObject remotesJSON;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> pinnedRemotes = sharedPreferences.getStringSet(context.getString(R.string.shared_preferences_pinned_remotes), new HashSet<>());
        Set<String> favoriteRemotes = sharedPreferences.getStringSet(context.getString(R.string.shared_preferences_drawer_pinned_remotes), new HashSet<>());

        try {
            process = getRuntimeProcess(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            process.waitFor();
            if (process.exitValue() != 0) {
                Toast.makeText(context, "error getting remotes", Toast.LENGTH_SHORT).show();
                return new ArrayList<>();
            }

            remotesJSON = new JSONObject(output.toString());
        } catch (IOException | InterruptedException | JSONException e) {
            Log.e(TAG, "getRemotes: error retrieving remotes", e);
            return new ArrayList<>();
        }

        List<RemoteItem> remoteItemList = new ArrayList<>();
        Iterator<String> iterator = remotesJSON.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            try {
                JSONObject remoteJSON = new JSONObject(remotesJSON.get(key).toString());
                String type = remoteJSON.optString("type");
                if (type.trim().isEmpty()) {
                    Toast.makeText(context, "error retrieving remotes", Toast.LENGTH_SHORT).show();
                    continue;
                }

                RemoteItem newRemote = new RemoteItem(key, type);
                if (type.equals("crypt") || type.equals("alias") || type.equals("cache")) {
                    newRemote = getRemoteType(remotesJSON, newRemote, key, 8);
                    if (newRemote == null) {
                        Toast.makeText(context, "error retrieving remote", Toast.LENGTH_SHORT).show();
                        continue;
                    }
                }

                if (pinnedRemotes.contains(newRemote.getName())) {
                    newRemote.pin(true);
                }

                if (favoriteRemotes.contains(newRemote.getName())) {
                    newRemote.setDrawerPinned(true);
                }

                remoteItemList.add(newRemote);
            } catch (JSONException e) {
                Log.e(TAG, "getRemotes: error decoding remotes", e);
                return new ArrayList<>();
            }
        }

        return remoteItemList;
    }

    public RemoteItem getRemoteItemFromName(String remoteName) {
        List<RemoteItem> remoteItemList = getRemotes();
        for (RemoteItem remoteItem : remoteItemList) {
            if (remoteItem.getName().equals(remoteName)) {
                return remoteItem;
            }
        }
        return null;
    }

    private RemoteItem getRemoteType(JSONObject remotesJSON, RemoteItem remoteItem, String remoteName, int maxDepth) {
        Iterator<String> iterator = remotesJSON.keys();

        while (iterator.hasNext()) {
            String key = iterator.next();

            if (!key.equals(remoteName)) {
                continue;
            }

            try {
                JSONObject remoteJSON = new JSONObject(remotesJSON.get(key).toString());
                String type = remoteJSON.optString("type");
                if (type.trim().isEmpty()) {
                    return null;
                }

                boolean recurse = true;
                switch (type) {
                    case "crypt":
                        remoteItem.setIsCrypt(true);
                        break;
                    case "alias":
                        remoteItem.setIsAlias(true);
                        break;
                    case "cache":
                        remoteItem.setIsCache(true);
                        break;
                    default:
                        recurse = false;
                }

                if (recurse && maxDepth > 0) {
                    String remote = remoteJSON.optString("remote");
                    if (remote.trim().isEmpty() || (!remote.contains(":") && !remote.startsWith("/"))) {
                        return null;
                    }

                    if (remote.startsWith("/")) { // local remote
                        remoteItem.setType("local");
                        remoteItem.setIsPathAlias(true);
                        return remoteItem;
                    } else {
                        int index = remote.indexOf(":");
                        remote = remote.substring(0, index);
                        return getRemoteType(remotesJSON, remoteItem, remote, --maxDepth);
                    }
                }
                remoteItem.setType(type);
                return remoteItem;
            } catch (JSONException e) {
                Log.e(TAG, "getRemoteType: error decoding remote type", e);
            }
        }

        return null;
    }

    public AboutResult aboutRemote(RemoteItem remoteItem) {
        String remoteName = remoteItem.getName() + ':';
        String[] command = createCommand("about", "--json", remoteName);
        StringBuilder output = new StringBuilder();
        AboutResult stats;
        Process process;
        JSONObject aboutJSON;

        try {
            process = getRuntimeProcess(command, getRcloneEnv());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            process.waitFor();
            if (0 != process.exitValue()) {
                Log.e(TAG, "aboutRemote: rclone error, exit(" + process.exitValue() + ")");
                Log.e(TAG, "aboutRemote: " + output);
                return new AboutResult();
            }

            aboutJSON = new JSONObject(output.toString());
        } catch (IOException | InterruptedException | JSONException e) {
            Log.e(TAG, "aboutRemote: unexpected error", e);
            return new AboutResult();
        }

        try {
            stats = new AboutResult(
                    aboutJSON.opt("used") != null ? aboutJSON.getLong("used") : -1,
                    aboutJSON.opt("total") != null ? aboutJSON.getLong("total") : -1,
                    aboutJSON.opt("free") != null ? aboutJSON.getLong("free") : -1,
                    aboutJSON.opt("trashed") != null ? aboutJSON.getLong("trashed") : -1
            );
        } catch (JSONException e) {
            Log.e(TAG, "aboutRemote: JSON format error ", e);
            return new AboutResult();
        }

        return stats;
    }

    public Process downloadFile(RemoteItem remote, FileItem downloadItem, String downloadPath) {
        String[] command;
        String remoteFilePath;
        String localFilePath;

        remoteFilePath = remote.getName() + ":";
        remoteFilePath += downloadItem.getPath();

        if (downloadItem.isDir()) {
            localFilePath = downloadPath + "/" + downloadItem.getName();
        } else {
            localFilePath = downloadPath;
        }

        localFilePath = encodePath(localFilePath);

        command = createCommandWithOptions("copy", remoteFilePath, localFilePath, "--transfers", "1", "--stats=1s", "--stats-log-level", "NOTICE", "--use-json-log");

        String[] env = getRcloneEnv();
        try {
            return getRuntimeProcess(command, env);
        } catch (IOException e) {
            Log.e(TAG, "downloadFile: error starting rclone", e);
            return null;
        }
    }

    public Process uploadFile(RemoteItem remote, String uploadPath, String uploadFile) {
        String remoteName = remote.getName();
        String path;
        String[] command;

        File file = new File(uploadFile);
        if (file.isDirectory()) {
            int index = uploadFile.lastIndexOf('/');
            String dirName = uploadFile.substring(index + 1);
            path = (uploadPath.compareTo("//" + remoteName) == 0) ? remoteName + ":" + dirName : remoteName + ":" + uploadPath + "/" + dirName;
        } else {
            path = (uploadPath.compareTo("//" + remoteName) == 0) ? remoteName + ":" : remoteName + ":" + uploadPath;
        }

        command = createCommandWithOptions("copy", uploadFile, path, "--transfers", "1", "--stats=1s", "--stats-log-level", "NOTICE", "--use-json-log");

        String[] env = getRcloneEnv();
        try {
            return getRuntimeProcess(command, env);
        } catch (IOException e) {
            Log.e(TAG, "uploadFile: error starting rclone", e);
            return null;
        }
    }

    @NonNull
    private String encodePath(String localFilePath) {
        if (localFilePath.indexOf('\u0000') < 0) {
            return localFilePath;
        }
        StringBuilder localPathBuilder = new StringBuilder(localFilePath.length());
        for (char c : localFilePath.toCharArray()) {
            if (c == '\u0000') {
                localPathBuilder.append('\u2400');
            } else {
                localPathBuilder.append(c);
            }

        }
        return localPathBuilder.toString();
    }

    public Boolean makeDirectory(RemoteItem remote, String path) {
        String newDir = remote.getName() + ":" + path;
        String[] command = createCommandWithOptions("mkdir", newDir);
        String[] env = getRcloneEnv();
        try {
            Process process = getRuntimeProcess(command, env);
            process.waitFor();
            if (process.exitValue() != 0) {
                return false;
            }
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "makeDirectory: error running rclone", e);
            return false;
        }
        return true;
    }

    public boolean copyConfigFile(Uri uri) throws IOException {
        String appsFileDir = context.getFilesDir().getPath();
        InputStream inputStream;
        // The exact cause of the NPE is unknown, but the effect is the same
        // - the copy process has failed, therefore bubble an IOException
        // for handling at the appropriate layers.
        try {
            inputStream = context.getContentResolver().openInputStream(uri);
        } catch (NullPointerException e) {
            throw new IOException(e);
        }
        File tempFile = new File(appsFileDir, "rclone.conf-tmp");
        File configFile = new File(appsFileDir, "rclone.conf");
        FileOutputStream fileOutputStream = new FileOutputStream(tempFile);

        byte[] buffer = new byte[4096];
        int offset;
        while ((offset = inputStream.read(buffer)) > 0) {
            fileOutputStream.write(buffer, 0, offset);
        }
        inputStream.close();
        fileOutputStream.flush();
        fileOutputStream.close();

        if (isValidConfig(tempFile.getAbsolutePath())) {
            if (!(tempFile.renameTo(configFile) && !tempFile.delete())) {
                throw new IOException();
            }
            return true;
        }
        return false;
    }

    public boolean isValidConfig(String path) {
        String[] command = {rclone, "-vvv", "--ask-password=false", "--config", path, "listremotes"};
        try {
            Process process = getRuntimeProcess(command);
            process.waitFor();
            if (process.exitValue() != 0) { //
                try (BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = stdOut.readLine()) != null || (line = stdErr.readLine()) != null) {
                        if (line.contains("could not parse line")) {
                            return false;
                        }
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            return false;
        }
        return true;
    }
}
