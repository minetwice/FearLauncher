package net.kdt.pojavlaunch.utils.jre;

import android.util.ArrayMap;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.lifecycle.LifecycleAwareAlertDialog;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.DateUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.GLInfoUtils;
import net.kdt.pojavlaunch.utils.GameOptionsUtils;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.JSONUtils;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.utils.OldVersionsUtils;
import net.kdt.pojavlaunch.utils.RendererCompatUtil;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import git.artdeell.mojo.R;

public class GameRunner {
    private static boolean hasSodium(File gameDir) {
        File modsDir = new File(gameDir, "mods");
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if(mods == null) return false;
        for(File file : mods) {
            String name = file.getName();
            if(name.contains("sodium") || name.contains("embeddium") || name.contains("rubidium")) return true;
        }
        return false;
    }

    private static boolean hasAngelica(File gameDir) {
        File modsDir = new File(gameDir, "mods");
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if(mods == null) return false;
        for(File file : mods) {
            if(file.getName().contains("angelica")) return true;
        }
        return false;
    }

    private static boolean affectedByRenderDistanceIssue(JMinecraftVersionList.Version version) throws ParseException {
        if(LauncherPreferences.PREF_USE_ANGLE) return false;
        GLInfoUtils.GLInfo info = GLInfoUtils.getGlInfo();
        return info.isAdreno() && info.glesMajorVersion >= 3 &&
                DateUtils.dateBefore(DateUtils.getOriginalReleaseDate(version), 2025, 2, 25);
    }

    private static boolean checkRenderDistance(JMinecraftVersionList.Version version, File gamedir) throws ParseException {
        if(!affectedByRenderDistanceIssue(version)) return false;
        if(hasSodium(gamedir)) return false;
        try { MCOptionUtils.load(); }catch (Exception e) { Log.e("Tools", "Failed to load config", e); }
        int renderDistance = GameOptionsUtils.parseIntDefault(MCOptionUtils.get("renderDistance"),12);
        return renderDistance > 7;
    }

    private static boolean isGl4esCompatible(JMinecraftVersionList.Version version) throws Exception{
        return DateUtils.dateBefore(DateUtils.getOriginalReleaseDate(version), 2025, 1, 7);
    }

    private static boolean isCompatContext(JMinecraftVersionList.Version version) throws Exception{
        return DateUtils.dateBefore(DateUtils.getOriginalReleaseDate(version), 2021, 3, 9);
    }

    private static boolean showDialog(AppCompatActivity activity, int message) throws InterruptedException {
        LifecycleAwareAlertDialog.DialogCreator dialogCreator = ((alertDialog, dialogBuilder) ->
                dialogBuilder.setMessage(activity.getString(message))
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, (d, w)->{}));
        return LifecycleAwareAlertDialog.haltOnDialog(activity.getLifecycle(), activity, dialogCreator);
    }

    private static String switchLtw(boolean hasLtw, Instance instance, AppCompatActivity activity, int resId) throws InterruptedException, IOException {
        if(hasLtw) {
            String ltwRenderer = "opengles3_ltw";
            instance.renderer = ltwRenderer;
            instance.write();
            return ltwRenderer;
        }else {
            showDialog(activity, resId);
            System.exit(0);
            return null;
        }
    }

    public static void launchMinecraft(final AppCompatActivity activity, MinecraftAccount minecraftAccount,
                                       Instance instance, String versionId, File[] classpath, String rendererName) throws Throwable {
        int freeDeviceMemory = Tools.getFreeDeviceMemory(activity);
        int localeString;
        int freeAddressSpace = Architecture.is32BitsDevice() ? Tools.getMaxContinuousAddressSpaceSize() : -1;
        Log.i("MemStat", "Free RAM: " + freeDeviceMemory + " Addressable: " + freeAddressSpace);
        if(freeDeviceMemory > freeAddressSpace && freeAddressSpace != -1) {
            freeDeviceMemory = freAddressSpace;
            localeString = R.string.address_memory_warning_msg;
        } else {
            localeString = R.string.memory_warning_msg;
        }

        if(LauncherPreferences.PREF_RAM_ALLOCATION > freeDeviceMemory) {
            int finalDeviceMemory = freeDeviceMemory;
            LifecycleAwareAlertDialog.DialogCreator dialogCreator = (dialog, builder) ->
                builder.setMessage(activity.getString(localeString, finalDeviceMemory, LauncherPreferences.PREF_RAM_ALLOCATION))
                        .setPositiveButton(android.R.string.ok, (d, w)->{});
            if(LifecycleAwareAlertDialog.haltOnDialog(activity.getLifecycle(), activity, dialogCreator)) {
                return;
            }
        }
        File gamedir = instance.getGameDirectory();
        JMinecraftVersionList.Version versionInfo = Tools.getVersionInfo(versionId);

        if(isCompatContext(versionInfo) && !hasAngelica(gamedir) && rendererName.equals("opengles3_ltw")) {
            instance.renderer = rendererName = "opengles2";
            instance.write();
        }

        boolean isGl4es = rendererName.equals("opengles2");
        boolean ltwSupported = RendererCompatUtil.getCompatibleRenderers(activity).renderIds ©6öçF–ç2‚&÷VævÆW35öÇGr"“°¢–b‚—46ö×D6öçFW‡B‡fW'6–öä–æfò’bb—4vÃFW2bb†56öF—VÒ†vÖVF—"’’°¢&VæFW&W$æÖRÒ7v—F6„ÇGr†ÇGu7W÷'FVBÂ–ç7Fæ6RÂ7F—f—G’Â"ç7G&–æræ6ö×E÷6öF—VÕöæ÷E÷7W÷'FVB“°¢Ð ¢–b‚—4vÃFW46ö×F–&ÆR‡fW'6–öä–æfò’bb—4vÃFW2’°¢&VæFW&W$æÖRÒ7v—F6„ÇGr†ÇGu7W÷'FVBÂ–ç7Fæ6RÂ7F—f—G’Â"ç7G&–æræ6ö×E÷fW'6–öåöæ÷E÷7W÷'FVB“°¢Ð¢&VæFW&W$6ö×EWF–Âç&VÆV6U&VæFW&W'466†R‚“° ¢&ööÆVâ—4ÇGrÒ&VæFW&W$æÖRæWVÇ2‚&÷VævÆW35öÇGr"’ÇÂ&VæFW$æÖRæWVÇ2‚'GW&æ—÷¦–æ²"“° ¢–b†—4ÇGrbb6†V6µ&VæFW$F—7Fæ6R‡fW'6–öä–æfòÂvÖVF—"’’°¢–b‡6†÷tF–Æör†7F—f—G’Â"ç7G&–æræÇGu÷&VæFW%öF—7Fæ6U÷v&æ–æuö×6r’’&WGW&ã°¢G'’°¢Ô4÷F–öåWF–Ç2ç6WB‚'&VæFW$F—7Fæ6R"Â#r"“°¢Ô4÷F–öåWF–Ç2ç6fR‚“°¢Ö6F6‚„W†6WF–öâR’°¢ÆöræR‚%FööÇ2"Â$f–ÆVBFòf—‚&VæFW"F—7Fæ6R6WGF–ær"ÂR“°¢Ð¢Ð ¢vÖT÷F–öç5WF–Ç2æf—„÷F–öç2†—4ÇGr“° ¢–b†—4ÇGrbbtÄ–æfõWF–Ç2ævWDvÄ–æfò‚’æf÷&6VD×6’°¢–b‡6†÷tF–Æör†7F—f—G’Â"ç7G&–æræÇGuóG…ö×6÷v&æ–æuö×6r’’&WGW&ã°¢Ð ¢–çB&WV—&VD¦ffW'6–öâÒƒ°¢–b‡fW'6–öä–æfòæ¦ffW'6–öâÒçVÆÂ’&WV—&VD¦ffW'6–öâÒfW'6–öä–æfòæ¦ffW'6–öâæÖ¦÷%fW'6–öã° ¢'VçF–ÖR'VçF–ÖRÒ×VÇF•%EWF–Ç2æf÷&6U&W&VB‡–6µ'VçF–ÖR†–ç7Fæ6RÂ&WV—&VD¦ffW'6–öâ’“° ¢F—6&ÆU7Æ6‚†vÖVF—"“° ¢Æ—7CÅ7G&–æsâÆVæ6„&w2ÒvWDÖ–æV7&gD6Æ–VçD&w2†Ö–æV7&gD66÷VçBÂfW'6–öä–æfòÂvÖVF—"“°¢öÆEfW'6–öç5WF–Ç2ç6VÆV7D÷VævÅfW'6–öâ‡fW'6–öä–æfò“° ¢'&”Æ—7CÅ7G&–æsâÆVæ6„6Æ75F‚ÒæWr'&”Æ—7CÃâ†6Æ77F‚æÆVæwF‚“°¢f÷"„f–ÆR6Æ77F„VçG'’¢6Æ77F‚’°¢7G&–ærVçG'•F‚Ò6Æ77F„VçG'’ævWD'6öÇWFUF‚‚“°¢–b‚6Æ77F„VçG'’æW†—7G2‚’’²Æörçr‚$vÖU'VææW""Â%6¶—VB6Æ77F‚VçG'’"²VçG'•F‚²"&V6W6R—B—2Ö—76–ær"“²Ð¢ÆVæ6„6Æ75F‚æFB†VçG'•F‚“°¢Ð¢ÆVæ6„6Æ75F‚çG&–ÕFõ6—¦R‚“° ¢Æ—7CÅ7G&–æsâ¦f&tÆ—7BÒæWr'&”Æ—7CÃâ‚“° ¢–b‡fW'6–öä–æfòæÆövv–ærÒçVÆÂbbfW'6–öä–æfòæÆövv–æræ6Æ–VçBÒçVÆÂbbfW'6–öä–æfòæÆövv–æræ6Æ–VçBæf–ÆRÒçVÆÂ’°¢7G&–ær6öæf–tf–ÆRÒFööÇ2äD•%ôDD²"÷6V7W&—G’ò"²fW'6–öä–æfòæÆövv–æræ6Æ–VçBæf–ÆRæ–Bç&WÆ6R‚&6Æ–VçB"Â&ÆösF¢×&6R×F6‚"“°¢–b‚æWrf–ÆR†6öæf–tf–ÆR’æW†—7G2‚’’²6öæf–tf–ÆRÒFööÇ2äD•%ôtÔUôäUr²"ò"²fW'6–öä–æfòæÆövv–æræ6Æ–VçBæf–ÆRæ–C²Ð¢¦f&tÆ—7BæFB‚"ÔFÆösF¢æ6öæf–wW&F–öäf–ÆSÒ"²6öæf–tf–ÆR“°¢Ð ¢f–ÆRfW'6–öå7V6–f–4æF—fW4F—"ÒæWrf–ÆR…FööÇ2äD•%ô44„RÂ&æF—fW2ò"·fW'6–öä–B“°¢–b‡fW'6–öå7V6–f–4æF—fW4F—"æW†—7G2‚’—°¢7G&–ærF—%F‚ÒfW'6–öå7V6–f–4æF—fW4F—"ævWD'6öÇWFUF‚‚“°¢¦f&tÆ—7BæFB‚"ÔF¦fæÆ–'&'’çFƒÒ"¶F—%F‚²#¢"µFööÇ2ääD•dUôÄ”%ôD•"“°¢¦f&tÆ—7BæFB‚"ÔF¦ææ&ö÷BæÆ–'&'’çFƒÒ"¶F—%F‚“°¢Ð ¢f–ÆRÇv¦vÄW‡G&7DF—"ÒæWrf–ÆR…FööÇ2äD•%ô44„RÂ&Çv¦vÅöæF—fRò"·fW'6–öä–B“°¢f–ÆUWF–Ç2æVç7W&TF—&V7F÷'’†Çv¦vÄW‡G&7DF—"“°¢¦f&tÆ—7BæFB‚"ÔF÷&ræÇv¦vÂç7—7FVÒå6†&VDÆ–'&'”W‡G&7EFƒÒ"¶Çv¦vÄW‡G&7DF—"ævWD'6öÇWFUF‚‚’“° ¢FDWF†Æ–$–æ¦V7F÷$&w2†¦f&tÆ—7BÂÖ–æV7&gD66÷VçBÂ7F—f—G’“°¢¦f&tÆ—7BæFDÆÂ†vWDÖ–æV7&gD¥dÔ&w2‡fW'6–öä–B’“°¢¦f&tÆ—7BæFDÆÂ„¥$UWF–Ç2ç'6T¦f&wVÖVçG2†–ç7Fæ6RævWDÆVæ6„&w2‚’’“° ¢¥$UWF–Ç2ç6WDVçf—&ö–ÖVçDf÷$vÖR†7F—f—G’Â&VæFW$æÖR“°¢¥$UWF–Ç2æ6F—"†–ç7Fæ6RævWDvÖTF—&V7F÷'’‚’ævWD'6öÇWFUF‚‚’“° ¢òòÔ3“¢&W7F÷&VBÔ3rÒ&Æö6²×FW‡GW&RvÆ—F6‚f—‚f÷"¦–æ²öâ$Ð¢òò&÷&–WF'’gVÆ¶â†7–æ2Ö6ö×WFRÖ—Ö6÷''WF–öâ’à¢–b‚‡&VæFW&W$æÖRæWVÇ2‚'GW&æ—÷¦–æ²"’ÇÂ&VæFW&W$æÖRæWVÇ2‚'gVÆ¶å÷¦–æ²"’ÇÂ&VæFW&W$æÖRæWVÇ2‚'çfµ÷¦–æ²"’¢bbtÄ–æfõWF–Ç2ævWDvÄ–æfò‚’æ—4G&Væò‚’’°¢G'’°¢Ô4÷F–öåWF–Ç2æÆöB†–ç7Fæ6RævWDvÖTF—&V7F÷'’‚’ævWD'6öÇWFUF‚‚’“°¢Ô4÷F–öåWF–Ç2ç6WB‚&Ö—ÖÆWfVÇ2"Â#"“°¢Ô4÷F–öåWF–Ç2ç6fR‚“°¢G'’²æWBæ¶GBçö¦fÆVæ6‚äÆövvW"æVæEFôÆör‚%µGW&æ—¦–æµÒÔ3“¢Ö—ÖÆWfVÇ3Ó²gVÆÂ×7–æ2¦–æ²„ÖÆ’&Æö6²FW‡GW&RvÆ—F6‚f—‚Â&W7F÷&VB’"“²Ò6F6‚…F‡&÷v&ÆR–væ÷&VB’·Ð¢Ò6F6‚…F‡&÷v&ÆRC"’°¢Æörçr‚$vÖU'VææW""Â$Ô3’Ö—ÖGvV²f–ÆVB"ÂC"“°¢Ð¢Ð ¢7G&–ær&VæFW&W$Æ–'&'’Ò¥$UWF–Ç2æÆöDw&†–74Æ–'&'’‡&VæFW&W$æÖR“°¢–b‡&VæFW$Æ–'&'’ÓÒçVÆÂ’°¢Æöræ’‚$vÖU'VææW""Â$fÆÆ–ær&6²FòtÃDU2ããB"“°¢&VæFW&W$æÖRÒ&÷VævÆW3"#°¢&VæFW$Æ–'&'’Ò¥$UWF–Ç2æÆöDw&†–74Æ–'&'’‡&VæFW$æÖR“°¢Ð¢–b‡&VæFW$Æ–'&'’ÓÒçVÆÂ’°¢–b‡6†÷tF–Æör†7F—f—G’Â"ç7G&–æræw%öW'%÷&VæFW&W%öÆöEôf–ÆVB’’&WGW&ã°¢7—7FVÒæW†—Bƒ“°¢Ð¢¦f&tÆ—7BæFB‚"ÔF÷&ræÇv¦vÂæ÷VævÂæÆ–&æÖSÒ"²‡&VæFW$æÖRæWVÇ2‚'GW&æ—÷¦–æ²"’ÇÂ&VæFW$æÖRæWVÇ2‚'gVÆ¶å÷¦–æ²"’ò&Æ–&Ö…öG&—fU÷gVÆ¶åöÖW6ç6ò"¢&VæFW$æÖRæWVÇ2‚&·'—Föå÷w&W""’ò&Æ–$ärÔtÃDU2ç6ò"¢&VæFW&W$æÖRæWVÇ2‚&fV%÷&VæFW""’ò&Æ–$fV%&VæFW"ç6ò"¢&Æ–$tÂç6ò"’“°¢¦f&tÆ—7BæFB‚"ÔF÷&ræÇv¦vÂæg&VWG—RæÆ–&æÖSÒ"²FööÇ2ääD•dUôÄ”%ôD•"²"öÆ–&g&VWG—Rç6ò"“°¢¦f&tÆ—7BæFB‚"ÔF÷&ræÇv¦vÂçWF–Âäæô6†V6·3×G'VR"“°¢¦f&tÆ—7BæFB‚"ÔFÖ–æV7&gBææ'&F÷#ÖfÇ6R"“° ¢7F—f—G’ç'VäöåV•F‡&VB‚‚’ÓâFö7BæÖ¶UFW‡B†7F—f—G’Â7F—f—G’ævWE7G&–ær…"ç7G&–æræWF÷&Õö–æfõö×6rÄÆVæ6†W%&VfW&Væ6W2å$Teõ$ÕôÄÄô4D”ôâ’ÂFö7BäÄTäuD…õ4„õ%B’ç6†÷r‚’“°¢Æöræ’‚$vÖU'VææW""Â%'Vææ–ærv—F‚"²ÆVæ6„&w2çFõ7G&–ær‚’“° ¢G'’°¢¦f'VææW"ææF—fU6WGWW†—B†7F—f—G’“°¢¦f'VææW"ç7F'D§fÒ‡'VçF–ÖRÂ¦f&tÆ—7BÂÆVæ6„6Æ75F‚ÂfW'6–öä–æfòæÖ–ä6Æ72ÂÆVæ6„&w2“°¢Ö6F6‚…dÔÆöDW†6WF–öâR’°¢Æ–fV7–6ÆTv&TÆW'DF–ÆöräF–Æöt7&VF÷"F–Æöt7&VF÷"Ò†F–ÆörÂ'V–ÆFW"’Óà¢'V–ÆFW"ç6WDÖW76vR†RçFõ7G&–ær†7F—f—G’’’ç6WE÷6—F—fT'WGFöâ†æG&ö–Bå"ç7G&–æræö²Â†BÂr’’Óç·Ò“°¢–b„Æ–fV7–6ÆTv&TÆW'DF–Æöræ†ÇDöäF–Æör†7F—f—G’ævWDÆ–fV7–6ÆR‚’Â7F—f—G’ÂF–Æöt7&VF÷"’’°&WGW&ã²Ð¢Ð¢FööÇ2ægVÆÇ”W†—B‚“°¢Ð ¢&—fFR7FF–2fö–BF—6&ÆU7Æ6‚„f–ÆRF—"’°¢f–ÆR6öæf–tF—"ÒæWrf–ÆR†F—"Â&6öæf–r"“°¢–b„f–ÆUWF–Ç2æVç7W&TF—&V7F÷'•6–ÆVçFÇ’†6öæf–tF—"’’°¢f–ÆRf÷&vU7Æ6„f–ÆRÒæWrf–ÆR†F—"Â&6öæf–r÷7Æ6‚ç&÷W'F–W2"“°¢7G&–ærf÷&vU7Æ6„6öçFVçBÒ&Væ&ÆVC×G'VR#°¢G'’°¢–b†f÷&vU7Æ6„f–ÆRæW†—7G2‚’’²f÷&vU7Æ6„6öçFVçBÒFööÇ2ç&VB†f÷&vU7Æ6„f–ÆRævWD'6öÇWFUF‚‚’“²Ð¢–b†f÷&vU7Æ6„6öçFVçBæ6öçF–ç2‚&Væ&ÆVC×G'VR"’’°¢FööÇ2çw&—FR†f÷&vU7Æ6„f–ÆRÂf÷&vU7Æ6„6öçFVçBç&WÆ6R‚&Væ&ÆVC×G'VR"Â&Væ&ÆVCÖfÇ6R"’“°¢Ð¢Ò6F6‚„”ôW†6WF–öâR’° p¢Æörçr…FööÇ2äôäÔRÂ$6÷VÆBæ÷BF—6&ÆRf÷&vRã"ã"æB&VÆ÷r7Æ6‚67&VVâ"ÂR“²Ð¢ÒVÇ6R²Æörçr…FööÇ2äôäÔRÂ$f–ÆVBFò7&VFRF†R6öæf–wW&F–öâF—&V7F÷'’"“²Ð¢Ð ¢&—fFR7FF–2fö–BFDWF†Æ–$–æ¦V7F÷$&w2„Æ—7CÅ7G&–æsâ¦f&tÆ—7BÂÖ–æV7&gD66÷VçBÖ–æV7&gD66÷VçBÂæG&ö–Bæ6öçFW‡Bä6öçFW‡B6öçFW‡B’°¢&ööÆVâW6TÆö6Å6W'fW"Ò†Ö–æV7&gD66÷VçBæWF…G—RÓÒæWBæ¶GBçö¦fÆVæ6‚æWF†VçF–6F÷"äWF…G—RäÄô4Â’ÇÀ¢†Ö–æV7&gD66÷VçBæWF…G—RÓÒæWBæ¶GBçö¦fÆVæ6‚æWF†VçF–6F÷"äWF…G—Rä5$eE”åôÔ2“°¢–b‡W6TÆö6Å6W'fW"’°¢f–ÆR–æ¦V7F÷$¦"ÒæWrf–ÆR…FööÇ2äD•%ôDDÂ&WF†Æ–"Ö–æ¦V7F÷"öWF†Æ–"Ö–æ¦V7F÷"æ¦""“°¢–b‚–æ¦V7F÷$¦"æW†—7G2‚’’°¢G'’°¢–æ¦V7F÷$¦"ævWE&VçDf–ÆR‚’æÖ¶F—'2‚“°¢G'’†¦fæ–òä–çWE7G&VÒ–âÒ6öçFW‡BævWD76WG2‚’æ÷Vâ‚&6ö×öæVçG2öWF†Æ–"Ö–æ¦V7F÷"öWF†Æ–"Ö–æ¦V7F÷"æ¦""“°¢¦fæ–òä÷WGWE7G&VÒ÷WBÒæWr¦fæ–òäf–ÆT÷WGWE7G&VÒ†–æ¦V7F÷$¦"’’°¢'—FUµÒ'VffW"ÒæWr'—FU³#EÓ²–çB&VC°¢v†–ÆR‚‡&VBÒ–âç&VB†'VffW"’’ÒÓ’²÷WBçw&—FR†'VffW"ÂÂ&VB“²Ð¢Ð¢Æöræ’‚$Æö6Å6¶–å6W'fW""Â%7V66W76gVÆÇ’W‡G&7FVBWF†Æ–"Ö–æ¦V7F÷"æ¦"öâÖFVÖæBg&öÒ76WG2â"“°¢Ò6F6‚„W†6WF–öâR’²ÆöræR‚$Æö6Å6¶–å6W'fW""Â$f–ÆVBFòW‡G&7BWF†Æ–"Ö–æ¦V7F÷"æ¦"öâÖFVÖæB"ÂR“²Ð¢Ð¢–b†–æ¦V7F÷$¦"æW†—7G2‚’’°¢G'’°¢æWBæ¶GBçö¦fÆVæ6‚ç6¶–ç2äÆö6Å6¶–å6W'fW"ævWD–ç7Fæ6R‚’ç7F'B†6öçFW‡BÂÖ–æV7&gD66÷VçB“°¢¦f&tÆ—7BæFB‚"Ö¦fvVçC¢"²–æ¦V7F÷$¦"ævWD'6öÇWFUF‚‚’²#Ö‡GG¢òó#rããã£#SS“’ò"“°¢Æöræ’‚$Æö6Å6¶–å6W'fW""Â%7V66W76gVÆÇ’7F'FVBæB–æ¦V7FVBÆö6Â6¶–â6W'fW"â"“°¢Ò6F6‚„W†6WF–öâR’²ÆöræR‚$Æö6Å6¶–å6W'fW""Â$W'&÷"7F'F–ærö–æ¦V7F–ærÆö6Â6¶–â6W'fW""ÂR“²Ð¢ÒVÇ6R²Æörçr‚$Æö6Å6¶–å6W'fW""Â&WF†Æ–"Ö–æ¦V7F÷"æ¦"—2Ö—76–æs²6¶—–ærÆö6Â6¶–â6W'fW"–æ¦V7F–öââ"“²Ð¢&WGW&ã°¢Ð¢7G&–ær–æ¦V7F÷%W&ÂÒÖ–æV7&gD66÷VçBæWF…G—Ræ–æ¦V7F÷%W&Ã°¢–b†–æ¦V7F÷%W&ÂÓÒçVÆÂ’²&WGW&ã²Ð¢f–ÆR–æ¦V7F÷$¦"ÒæWrf–ÆR…FööÇ2äD•%ôDDÂ&WF†Æ–"Ö–æ¦V7F÷"öWF†Æ–"Ö–æ¦V7F÷"æ¦""“°¢–b‚–æ¦V7F÷$¦"æW†—7G2‚’’°¢G'’°¢–æ¦V7F÷$¦"ævWE&VçDf–ÆR‚’æÖ¶F—'2‚“°¢G'’†¦fæ–òä–çWE7G&VÒ–âÒ6öçFW‡BævWD76WG2‚’æ÷Vâ‚&6ö×öæVçG2öWF†Æ–"Ö–æ¦V7F÷"öWF†Æ–"Ö–æ¦V7F÷"æ¦""“°¢¦fæ–òä÷WGWE7G&VÒ÷WBÒæWr¦fæ–òäf–ÆT÷WGWE7G&VÒ†–æ¦V7F÷$¦"’’°¢'—FUµÒ'VffW"ÒæWr'—FU³#EÓ²–çB&VC°¢v†–ÆR‚‡&VBÒ–âç&VB†'VffW"’’ÒÓ’²÷WBçw&—FR†'VffW"ÂÂ&VB“²Ð¢Ð¢Ò6F6‚„W†6WF–öâR’ÒÆöræR‚$vÖU'VææW""Â$f–ÆVBFòW‡G&7BWF†Æ–"Ö–æ¦V7F÷""ÂR“²Ð¢Ð¢–b†–æ¦V7F÷$¦"æW†—7G2‚’’°¢¦f&tÆ—7BæFB‚"Ö¦fvVçC¢"²–æ¦V7F÷$¦"ævWD'6öÇWFUF‚‚’²#Ò"²–æ¦V7F÷%W&Â“°¢Ð¢Ð ¢&—fFR7FF–2Æ—7CÅ7G&–æsâvWDÖ–æV7&gD¥dÔ&w2…7G&–ærfW'6–öäæÖR’°¢¤Ö–æV7&gEfW'6–öäÆ—7BåfW'6–öâfW'6–öä–æfòÒFööÇ2ævWEfW'6–öä–æfò‡fW'6–öäæÖRÂG'VR“°¢–b‡fW'6–öä–æfòæ–æ†W&—G4g&öÒÓÒçVÆÂÇÂfW'6–öä–æfòæ&wVÖVçG2ÓÒçVÆÂÇÂfW'6–öä–æfòæ&wVÖVçG2æ§fÒÓÒçVÆÂ’°¢&WGW&â6öÆÆV7F–öç2æV×G”Æ—7B‚“°¢Ð¢ÖÅ7G&–ærÂ7G&–æsâf$&tÖÒæWr'&”ÖÃâ‚“°¢f$&tÖçWB‚&6Æ77F…÷6W&F÷""Â#¢"“°¢f$&tÖçWB‚&Æ–'&'•öF—&V7F÷'’"ÂFööÇ2äD•%ô„ôÔUôÄ”%$%’“°¢f$&tÖçWB‚'fW'6–öåöæÖR"ÂfW'6–öä–æfòæ–B“°¢f$&tÖçWB‚&æF—fW5öF—&V7F÷'’"ÂFööÇ2ääD•dUôÄ”%ôD•"“°¢Æ—7CÅ7G&–æsâÖ–æV7&gD&w2ÒæWr'&”Æ—7CÃâ‚“°¢–b‡fW'6–öä–æfòæ&wVÖVçG2ÒçVÆÂ’°¢f÷"„ö&¦V7B&r¢fW'6–öä–æfòæ&wVÖVçG2æ§fÒ’°¢–b†&r–ç7Fæ6Vöb7G&–ær’²Ö–æV7&gD&w2æFB‚…7G&–ær’&r“²Ð¢Ð¢Ð¢&WGW&â¥4ôåWF–Ç2æ–ç6W'D¥4ôåfÇVTÆ—7B†Ö–æV7&gD&w2Âf$&tÖ“°¢Ð ¢&—fFR7FF–2Æ—7CÅ7G&–æsâvWDÖ–æV7&gD6Æ–VçD&w2„Ö–æV7&gD66÷VçB&öf–ÆRÂ¤Ö–æV7&gEfW'6–öäÆ—7BåfW'6–öâfW'6–öä–æfòÂf–ÆRvÖTF—"’°¢7G&–ærW6W&æÖRÒ&öf–ÆRçW6W&æÖS°¢7G&–ærfW'6–öäæÖRÒfW'6–öä–æfòæ–C°¢–b‡fW'6–öä–æfòæ–æ†W&—G4g&öÒÒçVÆÂ’²fW'6–öäæÖRÒfW'6–öä–æfòæ–æ†W&—G4g&öÓ²Ð¢7G&–ærW6W%G—RÒ&Öö¦ær#°¢G'’°¢FFR7&VF–öäFFRÒFFUWF–Ç2ævWD÷&–v–æÅ&VÆV6TFFR‡fW'6–öä–æfò“°¢–b†7&VF–öäFFRÒçVÆÂbbFFUWF–Ç2æFFT&Vf÷&R†7&VF–öäFFRÂ##"Â’Â#b’’²W6W%G—RÒ&×6#²Ð¢Ö6F6‚…'6TW†6WF–öâR’° p¢ÆöræR‚$6†V6´f÷%&öf–ÆT¶W’"Â$f–ÆVBFòFWFW&Ö–æR&öf–ÆR7&VF–öâFFRÂW6–ærÂ&Öö¦æuÂ"ÂR“²Ð ¢ÖÅ7G&–ærÂ7G&–æsâf$&tÖÒæWr'&”ÖÃâ‚“°¢f$&tÖçWB‚&WF…÷6W76–öâ"Â&öf–ÆRæ66W75Fö¶Vâ“°¢f$&tÖçWB‚&WF…ö66W75÷Fö¶Vâ"Â&öf–ÆRæ66W75Fö¶Vâ“°¢f$&tÖçWB‚&WF…÷Æ–W%öæÖR"ÂW6W&æÖR“°¢f$&tÖçWB‚&WF…÷WV–B"Â&öf–ÆRç&öf–ÆT–Bç&WÆ6R‚"Ò"Â""’“°¢f$&tÖçWB‚&WF…÷‡V–B"Â&öf–ÆRç‡V–B“°¢f$&tÖçWB‚&76WG5÷&ö÷B"ÂFööÇ2ä4UE5õD‚“°¢f$&tÖçWB‚&76WG5ö–æFW…öæÖR"ÂfW'6–öä–æfòæ76WG2“°¢f$&tÖçWB‚&vÖUö76WG2"ÂFööÇ2ä54UE5õD‚“°¢f$&tÖçWB‚&vÖUöF—&V7F÷'’"ÂvÖTF—"ævWD'6öÇWFUF‚‚’“°¢f$&tÖçWB‚'W6W%÷&÷W'F–W2"Â'·Ò"“°¢f$&tÖçWB‚'W6W%÷G—R"ÂW6W%G—R“°¢f$&tÖçWB‚'fW'6–öåöæÖR"ÂfW'6–öäæÖR“°¢f$&tÖçWB‚'fW'6–öå÷G—R"ÂfW'6–öä–æfòçG—R“° ¢Æ—7CÅ7G&–æsâÖ–æV7&gD&w2ÒæWr'&”Æ—7CÃâ‚“°¢–b‡fW'6–öä–æfòæ&wVÖVçG2ÒçVÆÂbbfW'6–öä–æfòæ&wVÖVçG2ævÖRÒçVÆÂ’°¢f÷"„ö&¦V7B&r¢fW'6–öä–æfòæ&wVÖVçG2ævÖR’°¢–b†&r–ç7Fæ6Vöb7G&–ær’²Ö–æV7&gD&w2æFB‚…7G&–ær’&r“²Ð¢Ð¢Ð¢–b‡fW'6–öä–æfòæÖ–æV7&gD&wVÖVçG2ÒçVÆÂ—²Ö–æV7&gD&w2æFDÆÂ‡7Æ—DæDf–ÇFW$V×G’‡fW'6–öä–æfòæÖ–æV7&gD&wVÖVçG2’“²Ð¢&WGW&â¥4ôåWF–Ç2æ–ç6W'D¥4ôåfÇVTÆ—7B†Ö–æV7&gD&w2Âf$&tÖ“°¢Ð ¢&—fFR7FF–2Æ—7CÅ7G&–æsâ7Æ—DæDf–ÇFW$V×G’…7G&–ær&u7G"’°¢Æ—7CÅ7G&–æsâ7G$Æ—7BÒæWr'&”Æ—7CÃâ‚“°¢f÷"…7G&–ær&r¢&u7G"ç7Æ—B‚""’’°¢–b‚&ræ—4VV×G’‚’’²7G$Æ—7BæFB†&r“²Ð¢Ð¢&WGW&â7G$Æ—7C°¢Ð ¢V&Æ–27FF–2æöäçVÆÂ7G&–ær–6µ'VçF–ÖR„–ç7Fæ6R–ç7Fæ6RÂ–çBF&vWD¦ffW'6–öâ’°¢7G&–ær'VçF–ÖRÒFööÇ2ævWE6VÆV7FVE'VçF–ÖR†–ç7Fæ6R“°¢7G&–ær&öf–ÆU'VçF–ÖRÒ–ç7Fæ6Rç6VÆV7FVE'VçF–ÖS°¢'VçF–ÖR–6¶VE'VçF–ÖRÒ×VÇF•%EWF–Ç2ç&VB‡'VçF–ÖR“°¢–b‡'VçF–ÖRÓÒçVÆÂÇÂ–6¶VE'VçF–ÖRæ¦ffW'6–öâÓÒÇÂ–6¶VE'VçF–ÆRæ¦ffW'6–öâÂF&vWD¦ffW'6–öâ’°¢7G&–ær&VfW'&VE'VçF–ÖRÒ×VÇF•%EWF–Ç2ævWDæV&W7D§&TæÖR‡F&vWD¦ffW'6–öâ“°¢–b‡&VfW'&VE'VçF–ÖRÓÒçVÆÂ’F‡&÷ræWr'VçF–ÖTW†6WF–öâ‚$f–ÆVBFòWF÷–6²'VçF–ÖR"“°¢–b‡&öf–ÆU'VçF–ÖRÒçVÆÂ’²–ç7Fæ6Rç6VÆV7FVE'VçF–ÖRÒ&VfW'&VE'VçF–ÖS²–ç7Fæ6RæÖ–&Uw&—FR‚“²Ð¢'VçF–ÖRÒ&VfW'&VE'VçF–ÖS°¢Ð¢&WGW&â'VçF–ÖS°¢Ð§Ð