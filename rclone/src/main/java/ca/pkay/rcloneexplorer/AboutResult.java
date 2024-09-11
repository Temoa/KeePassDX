package ca.pkay.rcloneexplorer;

import androidx.annotation.NonNull;

public class AboutResult {
    private final long used;
    private final long total;
    private final long free;
    private final long trashed;
    private boolean failed;

    public AboutResult(long used, long total, long free, long trashed) {
        this.used = used;
        this.total = total;
        this.free = free;
        this.trashed = trashed;
        this.failed = false;
    }

    public AboutResult() {
        this(-1, -1, -1, -1);
        this.failed = true;
    }

    public long getUsed() {
        return used;
    }

    public long getTotal() {
        return total;
    }

    public long getFree() {
        return free;
    }

    public long getTrashed() {
        return trashed;
    }

    public boolean hasFailed() {
        return failed;
    }

    public String convertFileSize(long fileSize) {
        double kiloByte = 1024;
        double megaByte = kiloByte * 1024;
        double gigaByte = megaByte * 1024;
        double teraByte = gigaByte * 1024;

        if (fileSize < kiloByte) {
            return fileSize + " Bytes";
        } else if (fileSize < megaByte) {
            return String.format("%.3f KiB", fileSize / kiloByte);
        } else if (fileSize < gigaByte) {
            return String.format("%.3f MiB", fileSize / megaByte);
        } else if (fileSize < teraByte) {
            return String.format("%.3f GiB", fileSize / gigaByte);
        } else {
            return String.format("%.3f TiB", fileSize / teraByte);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "Total: " + convertFileSize(total)
                + "\nUsed: " + convertFileSize(used)
                + "\nFree: " + convertFileSize(free)
                + "\nTrashed: " + convertFileSize(trashed);
    }
}
