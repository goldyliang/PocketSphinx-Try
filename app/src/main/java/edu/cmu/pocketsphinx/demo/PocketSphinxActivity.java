/* ====================================================================
 * Copyright (c) 2014 Alpha Cephei Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY ALPHA CEPHEI INC. ``AS IS'' AND
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
 * NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 */

package edu.cmu.pocketsphinx.demo;

import static android.widget.Toast.makeText;
import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

import android.app.Activity;
import android.media.AudioRecord;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import java.lang.reflect.Field;

public class PocketSphinxActivity extends Activity implements
        RecognitionListener {
		
    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "wakeup";
    private static final String FORECAST_SEARCH = "forecast";
    private static final String DIGITS_SEARCH = "digits";
    private static final String SYLLABLES_SEARCH = "syllables";
    private static final String PHONE_SEARCH = "phones";
    private static final String MENU_SEARCH = "menu";
    private static final String SYLLABLES_KWS_SEARCH = "syllables_kws";
    
    /* Keyword we are looking for to activate menu */
    private static final String KEYPHRASE = "oh mighty computer";

    private SpeechRecognizer recognizer;
    private HashMap<String, Integer> captions;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        /* Test AudioRecord */

        //recorder.startRecording();

        //recorder.get

        // Prepare the data for UI
        captions = new HashMap<String, Integer>();
        captions.put(KWS_SEARCH, R.string.kws_caption);
        captions.put(MENU_SEARCH, R.string.menu_caption);
        captions.put(DIGITS_SEARCH, R.string.digits_caption);
        captions.put(PHONE_SEARCH, R.string.phone_caption);
        captions.put(FORECAST_SEARCH, R.string.forecast_caption);
        captions.put(SYLLABLES_SEARCH, R.string.syllable_caption);
        captions.put(SYLLABLES_KWS_SEARCH, R.string.syllable_caption);
        setContentView(R.layout.main);
        ((TextView) findViewById(R.id.caption_text))
                .setText("Preparing the recognizer");

        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task

        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {

                AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone( 16000, 1024, 0);//22050,1024,0);

                outFile = new File(getApplicationContext().getFilesDir(), "record.out");

                System.out.println("Output file:" + outFile.getAbsolutePath());
                //final PrintWriter writer;

                //syllableOutFile = new File (getApplicationContext().getFilesDir(), "syllable.out");

                try {
                    //writer = new PrintWriter(file);
                    writer = new PrintWriter(outFile);
                    //writer= new PrintWriter(System.out);
                    //syllableOutWriter=new PrintWriter(System.out);
                } catch (Exception e) {
                    return e;
                }

                PitchDetectionHandler pdh = new PitchDetectionHandler() {
                    @Override
                    public void handlePitch(PitchDetectionResult result, AudioEvent e) {
                        final float pitchInHz = result.getPitch();
                        final long time = System.currentTimeMillis();
                        System.out.println("Pitch," + String.valueOf(time) + "," + String.valueOf(pitchInHz));//)%d,%f")
                        //writer.printf("%d,%f", time, pitchInHz);
                        /*runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView text = (TextView) findViewById(R.id.result_text);
                                text.setText( " " + pitchInHz);
                                //System.currentTimeMillis()
                            }
                        }); */
                    }
                };
                AudioProcessor p = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, pdh);
                dispatcher.addAudioProcessor(p);
                new Thread(dispatcher,"Audio Dispatcher").start();

                AudioRecordFromDispatcher recordFromDispatcher = new AudioRecordFromDispatcher(dispatcher, 6, 16000, 16, 2, 6400 * 2);
                        //22050, 16, 2, 6400 * 2);

                try {
                    Assets assets = new Assets(PocketSphinxActivity.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);

                    // Replace the recorder with the one from dispatcher
                    Field f = recognizer.getClass().getDeclaredField("recorder");
                    f.setAccessible(true);
                    f.set(recognizer, recordFromDispatcher);
                } catch (IOException e) {
                    return e;
                } catch (NoSuchFieldException e) {
                    return e;
                } catch (IllegalAccessException e) {
                    return e;
                }

                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    ((TextView) findViewById(R.id.caption_text))
                            .setText("Failed to init recognizer " + result);
                } else {
                    //switchSearch(KWS_SEARCH);
                    switchSearch(SYLLABLES_SEARCH);
                    //switchSearch(SYLLABLES_KWS_SEARCH);
                }
            }
        }.execute();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        recognizer.cancel();
        recognizer.shutdown();
    }

    private String cacheResult = "";
    private long lastCached = 0;
    private File outFile;
    private PrintWriter writer;

    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
    	    return;

        String text = hypothesis.getHypstr();

        if (text.length() <= cacheResult.length())
            return;

        int indx = text.indexOf(cacheResult);

        long time = System.currentTimeMillis();
        String syllable = "";
        if (indx==0) {
            // We may get a new syllable
            if (text.length() - cacheResult.length() > 1) {
                time = System.currentTimeMillis();
                syllable = text.substring(cacheResult.length());
            }
        } else {
            // We may get a new result of the previous recognized syllable
            int i = cacheResult.lastIndexOf(' ');
            if (i<0) {
                cacheResult = " " + cacheResult;
                i = 0;
            }
            String tmpText =  text;
            int j = text.lastIndexOf(' ');
            if (j<0) {
                tmpText = " " + text;
                j = 0;
            }

            if (cacheResult.substring(0,i).equals(tmpText.substring(0,j))) {
                time = lastCached;
                syllable = tmpText.substring(j+1);
            }
        }

        if (! syllable.isEmpty())
            //System.out.println("Syllable," + String.valueOf(time) + "," + syllable);
            writer.printf("Syllable,%d,%s",time,syllable);

        lastCached = time;
        cacheResult = text;
        /*
        if (text.equals(KEYPHRASE))
            switchSearch(MENU_SEARCH);
        else if (text.equals(DIGITS_SEARCH))
            switchSearch(DIGITS_SEARCH);
        else if (text.equals(PHONE_SEARCH))
            switchSearch(PHONE_SEARCH);
        else if (text.equals(FORECAST_SEARCH))
            switchSearch(FORECAST_SEARCH);
        else
            //((TextView) findViewById(R.id.result_text)).setText(text);
            System.out.println("Partial result:" + text); */
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        ((TextView) findViewById(R.id.result_text)).setText("");
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            System.out.println("onResult:" + text);
            //makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {
        //if (!recognizer.getSearchName().equals(KWS_SEARCH))
        //    switchSearch(KWS_SEARCH);
    }

    private void switchSearch(String searchName) {
        recognizer.stop();
        
        // If we are not spotting, start listening with timeout (10000 ms or 10 seconds).
        if (searchName.equals(KWS_SEARCH))
            recognizer.startListening(searchName);
        else
            recognizer.startListening(searchName, 10000);

        String caption = getResources().getString(captions.get(searchName));
        ((TextView) findViewById(R.id.caption_text)).setText(caption);
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them
        
        recognizer = defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))
                
                // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .setRawLogDir(assetsDir)
                
                // Threshold to tune for keyphrase to balance between false alarms and misses
                //.setKeywordThreshold(1e-45f)
                
                // Use context-independent phonetic search, context-dependent is too slow for mobile
                .setBoolean("-allphone_ci", true)
                
                .getRecognizer();
        recognizer.addListener(this);

        /** In your application you might not need to add all those searches.
         * They are added here for demonstration. You can leave just one.
         */

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);
        
        // Create grammar-based search for selection between demos
        File menuGrammar = new File(assetsDir, "menu.gram");
        recognizer.addGrammarSearch(MENU_SEARCH, menuGrammar);

        // Create grammar-based search for digit recognition
        File digitsGrammar = new File(assetsDir, "digits.gram");
        recognizer.addGrammarSearch(DIGITS_SEARCH, digitsGrammar);

        File syllableGrammar = new File(assetsDir, "syllables.gram");
        recognizer.addGrammarSearch(SYLLABLES_SEARCH, syllableGrammar);

        File syllableKeywords = new File(assetsDir, "syllables.kws");
        recognizer.addKeywordSearch(SYLLABLES_KWS_SEARCH, syllableKeywords);
        
        // Create language model search
        File languageModel = new File(assetsDir, "weather.dmp");
        recognizer.addNgramSearch(FORECAST_SEARCH, languageModel);
        
        // Phonetic search
        File phoneticModel = new File(assetsDir, "en-phone.dmp");
        recognizer.addAllphoneSearch(PHONE_SEARCH, phoneticModel);
    }

    @Override
    public void onError(Exception error) {
        ((TextView) findViewById(R.id.caption_text)).setText(error.getMessage());
    }

    @Override
    public void onTimeout() {
        //switchSearch(KWS_SEARCH);
    }
}
