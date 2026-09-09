package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.res.XmlResourceParser;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RendererCompatUtil {
    public static class CompatRenderers {
        public final List<String> rendererIds;
        public final List<String> rendererNames;
        public CompatRenderers(List<String> rendererIds, List<String> rendererNames) {
            this.rendererIds = rendererIds;
            this.rendererNames = rendererNames;
        }
    }

    private static CompatRenderers cachedRenderers;
    private static String cachedRenderersAbi;

    public static CompatRenderers getCompatibleRenderers(Context context) {
        String currentAbi = net.kdt.pojavlaunch.Architecture.is32BitsDevice() ? "arm" : "arm64";
        if(cachedRenderers != null && cachedRenderersAbi.equals(currentAbi)) return cachedRenderers;
        cachedRenderersAbi = currentAbi;
        List<String> rendererIds = new ArrayList<>();
        List<String> rendererNames = new ArrayList<>();
        try {
            XmlResourceParser parser = context.getResources().getXml(
                    net.kdt.pojavlaunch.R.xml.renderer_compat);
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xpp = factory.newPullParser();
            xpp.setInput(parser);
            int eventType = xpp.getEventType();
            String currentRendererId = null;
            while(eventType != XmlPullParser.END_DOCUMENT) {
                if(eventType == XmlPullParser.START_TAG) {
                    if(xpp.getName().equals("renderer")) {
                        for(int i = 0; i < xpp.getAttributeCount(); i++) {
                            if(xpp.getAttributeName(i).equals("id")) {
                                currentRendererId = xpp.getAttributeValue(i);
                            }
                        }
                    }
                } else if(eventType == XmlPullParser.TEXT) {
                    if(currentRendererId != null) {
                        String abi = xpp.getText();
                        if(abi.equals(currentAbi) || abi.equals("all")) {
                            if(currentRendererId.startsWith("turbo")) {
                                rendererIds.add(currentRendererId);
                                rendererNames.add(currentRendererId);
                            } else {
                                rendererIds.add(currentRendererId);
                                rendererNames.add(currentRendererId);
                            }
                        }
                        currentRendererId = null;
                    }
                }
                eventType = xpp.next();
            }
        } catch (XmlPullParserException | IOException e) {
            // ignore
        }
        cachedRenderers = new CompatRenderers(rendererIds, rendererNames);
        return cachedRenderers;
    }

    public static void releaseRenderersCache() {
        cachedRenderers = null;
        cachedRenderersAbi = null;
    }
}
