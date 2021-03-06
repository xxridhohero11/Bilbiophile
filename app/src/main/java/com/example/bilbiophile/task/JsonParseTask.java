package com.example.bilbiophile.task;

import android.content.Context;
import android.os.Process;
import android.util.JsonReader;
import android.util.JsonToken;

import com.example.bilbiophile.R;
import com.example.bilbiophile.model.data.Book;
import com.example.bilbiophile.model.data.Chapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class JsonParseTask extends BaseTask<InputStream, Void, Book> {
    private static final String TAG = "___JsonParse";
    private static final String URL_DOWNLOAD_PREFIX = "https://archive.org/download/";


    private static final String JSON_FILES = "files";    // array
    private static final String JSON_FILES_NAME = "name";
    private static final String JSON_FILES_SOURCE = "source";
    private static final String JSON_FILES_TITLE = "title";
    private static final String JSON_FILES_TRACK = "track";
    private static final String JSON_FILES_SIZE = "size";
    private static final String JSON_FILES_LENGTH = "length";
    private static final String JSON_FILES_ARTIST = "artist";

    private static final String JSON_METADATA = "metadata";  // object
    private static final String JSON_METADATA_CREATOR = "creator";
    private static final String JSON_METADATA_DESCRIPTION = "description";
    private static final String JSON_METADATA_PUBDATE = "publicdate";
    private static final String JSON_METADATA_RUNTIME = "runtime";




    private List<OnJsonParseListener> mListeners = new ArrayList<>();
    private Book mBook;


    public JsonParseTask(Context context, int taskId, Book book) {
        super(context, taskId);
        this.mBook = book;
    }


    public void addListener(OnJsonParseListener listener) {
        mListeners.add(listener);
    }


    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        for (OnJsonParseListener listener: mListeners) {
            listener.onPreJsonParse(mTaskId);
        }
    }


    @Override
    protected Book doInBackground(InputStream... params) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND + Process.THREAD_PRIORITY_MORE_FAVORABLE);

        try {
            return parse(params[0]);
        } catch (Exception ex) {
            for (OnJsonParseListener listener: mListeners) {
                listener.onJsonParseError(mTaskId, mContext.getString(R.string.msg_network_error));
            }
            return null;
        }
    }


    private Book parse(InputStream ins) throws Exception {
        JsonReader reader = new JsonReader(new InputStreamReader(ins, StandardCharsets.UTF_8));
        try {
            return readBookObject(reader);
        } finally {
            reader.close();
            ins.close();
        }
    }


    public Book readBookObject(JsonReader reader) throws IOException {
        reader.beginObject();

        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals(JSON_FILES)) {
                // clear all previous chapter
                mBook.chapters.clear();
                readFilesArray(reader);
            } else if (name.equals(JSON_METADATA)) {
                readMetadataObject(reader);
            } else {
                reader.skipValue();
            }
        }

        reader.endObject();

        // sort before returning
        Collections.sort(mBook.chapters, new Comparator<Chapter>() {
            @Override
            public int compare(Chapter left, Chapter right) {
                // -1 - less than, 1 - greater than, 0 - equal
                if (left.trackNumber == right.trackNumber) { return 0; }
                else if (left.trackNumber < right.trackNumber) { return -1; }
                else { return 1; }
            }
        });

        return mBook;
    }

    private void readFileObject(JsonReader reader) throws IOException {
        reader.beginObject();

        String fName = "";
        String fSource = "";
        String fTitle = "";
        String fTrack = "";
        long fSize = 0;
        double fLength = 0.0;
        String fArtist = "";

        boolean isSkipped = false;
        while (reader.hasNext()) {
            if (isSkipped) {
                // skip this object, ignore all other
                reader.nextName();
                reader.skipValue();
                continue;
            }

            String name = reader.nextName();

            if (name.equals(JSON_FILES_NAME) && reader.peek() != JsonToken.NULL) {
                fName = reader.nextString();
            } else if (name.equals(JSON_FILES_SOURCE) && reader.peek() != JsonToken.NULL) {
                fSource = reader.nextString();
                if (!fSource.equals("original")) {
                    // not original, skip this object
                    isSkipped = true;
                    continue;
                }
            } else if (name.equals(JSON_FILES_TITLE) && reader.peek() != JsonToken.NULL) {
                fTitle = reader.nextString();
            } else if (name.equals(JSON_FILES_TRACK) && reader.peek() != JsonToken.NULL) {
                fTrack = reader.nextString();
            } else if (name.equals(JSON_FILES_SIZE) && reader.peek() != JsonToken.NULL) {
                fSize = reader.nextLong();
            } else if (name.equals(JSON_FILES_LENGTH) && reader.peek() != JsonToken.NULL) {
                fLength = reader.nextDouble();
            } else if (name.equals(JSON_FILES_ARTIST) && reader.peek() != JsonToken.NULL) {
                fArtist = reader.nextString();
            } else {
                reader.skipValue();
            }
        }

        reader.endObject();

        if (!isSkipped && !fTrack.isEmpty()) {
            // real chapter
            Chapter chapter = new Chapter();

            String[] segs = fTrack.split("/");
            try {
                chapter.trackNumber = Integer.parseInt(segs[0]);
            } catch (Exception ex) {}

            chapter.name = fName;
            chapter.url = URL_DOWNLOAD_PREFIX + mBook.guid + "/" + chapter.name;
            chapter.title = fTitle;
            chapter.artist = fArtist;
            chapter.trackSize = fSize;
            chapter.trackLength = fLength;
            chapter.album = mBook.title;
            chapter.guid = mBook.guid;

            mBook.chapters.add(chapter);
        }
    }

    private void readFilesArray(JsonReader reader) throws IOException {
        reader.beginArray();

        while (reader.hasNext()) {
            readFileObject(reader);
        }

        reader.endArray();
    }

    private void readMetadataObject(JsonReader reader) throws IOException {
        reader.beginObject();

        String fCreator = "";
        String fDescription = "";
        String fPubdate = "";
        String fRuntime = "";

        while (reader.hasNext()) {
            String name = reader.nextName();

            if (name.equals(JSON_METADATA_CREATOR) && reader.peek() != JsonToken.NULL) {
                fCreator = reader.nextString();
            } else if (name.equals(JSON_METADATA_DESCRIPTION) && reader.peek() != JsonToken.NULL) {
                fDescription = reader.nextString();
            } else if (name.equals(JSON_METADATA_PUBDATE) && reader.peek() != JsonToken.NULL) {
                fPubdate = reader.nextString();
            } else if (name.equals(JSON_METADATA_RUNTIME) && reader.peek() != JsonToken.NULL) {
                fRuntime = reader.nextString();
            } else {
                reader.skipValue();
            }
        }

        reader.endObject();

        // re-update mBook
        mBook.creator = fCreator;
        mBook.description = fDescription;

        mBook.runtime = fRuntime;
    }


    @Override
    protected void onPostExecute(Book result) {
        super.onPostExecute(result);

        for (OnJsonParseListener listener: mListeners) {
            listener.onPostJsonParse(mTaskId, result);
        }
    }


    public interface OnJsonParseListener {
        void onPreJsonParse(int taskId);
        void onPostJsonParse(int taskId, Book result);
        void onJsonParseError(int taskId, String message);
    }
}

