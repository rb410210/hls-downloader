package com.rohitbalan.hlsdownloader;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PlaylistDownloader {
    private URL url;
    private List<String> playlist;
    private Crypto crypto;

    private static String EXT_X_KEY = "#EXT-X-KEY";
    private static final String BANDWIDTH = "BANDWIDTH";

    public PlaylistDownloader(String playlistUrl) throws MalformedURLException {
        this.url = new URL(playlistUrl);
        this.playlist = new ArrayList<String>();
    }

    public void download(String outfile) throws IOException {
        this.download(outfile, null);
    }

    public void download(String outfile, String key) throws IOException {
        fetchPlaylist();

        this.crypto = new Crypto(getBaseUrl(this.url), key);

        for (String line : playlist) {
            line = line.trim();

            if (line.startsWith(EXT_X_KEY)) {
                crypto.updateKeyString(line);

                System.out.printf("\rCurrent Key: %s                                  \n", crypto.getCurrentKey());
                System.out.printf("Current IV:  %s\n", crypto.getCurrentIV());
            } else if (line.length() > 0 && !line.startsWith("#")) {
                URL segmentUrl;

                if (!line.startsWith("http")) {
                    String baseUrl = getBaseUrl(this.url);
                    segmentUrl = new URL(baseUrl + line);
                } else {
                    segmentUrl = new URL(line);
                }

                downloadInternal(segmentUrl, outfile);
            }
        }

        System.out.println("\nDone.");
    }

    private void downloadInternal(URL segmentUrl, String outFile) throws IOException {
        byte[] buffer = new byte[512];

        InputStream is = crypto.hasKey()
                ? crypto.wrapInputStream(segmentUrl.openStream())
                : segmentUrl.openStream();

        FileOutputStream out;

        if (outFile != null) {
            File file = new File(outFile);
            out = new FileOutputStream(outFile, file.exists());
        } else {
            String path = segmentUrl.getPath();
            int pos = path.lastIndexOf('/');
            out = new FileOutputStream(path.substring(++pos), false);
        }

        System.out.printf("Downloading segment: %s\r", segmentUrl);

        int read;

        while ((read = is.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }

        is.close();
        out.close();
    }

    private String getBaseUrl(URL url) {
        String urlString = url.toString();
        int index = urlString.lastIndexOf('/');
        return urlString.substring(0, ++index);
    }

    private void fetchPlaylist() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
        boolean isMaster = false;
        long maxRate = 0L;
        int maxRateIndex = 0;

        String line;
        int index = 0;

        while ((line = reader.readLine()) != null) {
            playlist.add(line);

            if (line.contains(BANDWIDTH))
                isMaster = true;

            if (isMaster && line.contains(BANDWIDTH)) {
                try {
                    int pos = line.lastIndexOf("=");
                    long bandwidth = Long.parseLong(line.substring(++pos));

                    maxRate = Math.max(bandwidth, maxRate);

                    if (bandwidth == maxRate)
                        maxRateIndex = index + 1;
                } catch (NumberFormatException ignore) {}
            }

            index++;
        }

        reader.close();

        if (isMaster) {
            System.out.printf("Found master playlist, fetching highest stream at %dKb/s\n", maxRate / 1024);
            this.url = updateUrlForSubPlaylist(playlist.get(maxRateIndex));
            this.playlist.clear();

            fetchPlaylist();
        }
    }

    private URL updateUrlForSubPlaylist(String sub) throws MalformedURLException {
        String newUrl;

        if (!sub.startsWith("http")) {
            newUrl = getBaseUrl(this.url) + sub;
        } else {
            newUrl = sub;
        }

        return new URL(newUrl);
    }
}
