//
// Created by maks on 05.06.2023.
//
#include "nsbypass.h"
#include "android_namespace_func.h"
#include <dlfcn.h>
#include <android/dlext.h>
#include <android/log.h>
#include <sys/mman.h>
#include <sys/user.h>
#include <string.h>
#include <stdio.h>
#include <linux/limits.h>
#include <errno.h>
#include <unistd.h>
#include <asm/unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <elf.h>
#include <elf_defs.h>
#include <inttypes.h>

#define TAG __FILE_NAME__
#include <log.h>

/* Library search path */
#define SEARCH_PATH "/system/lib64"

static struct android_namespace_t* driver_namespace = NULL;

bool linker_ns_load(const char* lib_search_path) {
    if(driver_namespace != NULL) return true; // Do not initialize namespaces multiple times, this caused very funny bugs we spent hours debugging
    android_ldfuncs_t ldfuncs;
    if(!locate_namespace_funcs(&ldfuncs)) {
        return false;
    }

    // assemble the full path search path
    char full_path[strlen(SEARCH_PATH) + strlen(lib_search_path) + 2 + 1];
    sprintf(full_path, "%s:%s", SEARCH_PATH, lib_search_path);
    driver_namespace = ldfuncs.create_namespace("pojav-driver",
                                                        full_path,
                                                       full_path,
                                                       3 /* TYPE_SHAFED | TYPE_ISOLATED */,
                                                      "/system/:/data/:/vendor/:/apex/", NULL);
    // THIS IS VERY IMPORTANT and how I trolled FoldCraft:
    // You need to link the new driver_namespace with NULL and and add ld-android.so
    // in the link list, to pass through the driver_namespace correctly.
    // Not doing this fucks up internal __loader symbol lookup
    // inside the new driver_namespace, thus breaking it on
    // a lot of android versions
    // FoldCraft got trolled because they copied the
    // old broken code verbatim and didn't even test it thoroughly
    ldfuncs.link_namespaces(driver_namespace, NULL, "ld-android.so");
    // Also establish links to use the libnativeloader(_lazy).so libraries
    // from the global namespace. This is a workaround for an EMUI issue where
    // the newly loaded libnativeloader_lazy for some unknown reason links
    // to itself and causes a deadlock when loading the vulkan driver.
    ldfuncs.link_namespaces(driver_namespace, NULL, "libnativeloader.so");
    ldfuncs.link_namespaces(driver_namespace, NULL, "libnativeloader_lazy.so");
    ldfuncs.close(ldfuncs.dl_handle);
    return true;
}

void* linker_ns_dlopen(const char* name, int flag) {
    android_dlextinfo dlextinfo;
    dlextinfo.flags = ANDROID_DLEXT_USE_NAMESPACE;
    dlextinfo.library_namespace = driver_namespace;
    return android_dlopen_ext(name, flag, &dlextinfo);
}

bool patch_elf_soname(int patchfd, int realfd, size_t size, const char* patchname) {
    char* target = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, patchfd, 0);
    if(!target) return false;
    if(read(realfd, target, size) != size) goto fail;


    ELF_EHDR *ehdr = (ELF_EHDR*)target;
    ELF_SHDR *shtr = (ELF_SHDR*)(target + ehdr->e_shoff);
    for(ELF_HALF i = 0; i < ehdr->e_shnum; i++) {
        ELF_SHD*Z€H	њЪ–ЪWNВ€YЉ‹OњЪЭ\HOHТСSђSRPКHВ€Ъ\Љ€ЭќX€H\™Щ]
ИЪ–Ъ‹OњЪЫ[љЧKњЪЫЩ™њЩ]В€ЛИY€\™IЬИHШ\›љ[™И™[ЭЛ]	ЬИ›ЩЭ\ЛYЫ›Ь™H]€S—СS€
™[‘[ќљY\ИH
S—СSЉЉJ\™Щ]
И‹OњЪЫЩ™њЩ]
NВ€›ЬЉS—ЦУФ‘ИHИИ
‹OњЪЬЪ^™HИ‹OњЪЩ[ќЪ^™JNЪКККHВ€S—СSЉ€[‘[ќћHH	™[‘[ќљY\ЦЪЧNВ€YЉ[‘[ќћKO™ЭYИOHФУУђSQJHВ€Ъ\Љ€ЫЫ[YHHЭќX€
И[‘[ќћKO™Э[‹™Э[В€Ъ^™WЭЫЫ[YWЫ[€HЭ›[ЉЫЫ[YJNВ€Ъ^™WЭ]Ъ[YWЫ[€HЭ›[Љ]Ъ[YJNВ€YЉ]Ъ[YWЫ[€OHЫЫ[YWЫ[ЉHЫЭИZ[В‚€ЭЬJЫЫ[YK]Ъ[YJNВ€][›X\
\™Щ]Ъ^™JNВ€™]\›€ќYNВ€B€B€B€B‚€Z[‚€][›X\
\™Щ]Ъ^™JNВ€™]\›€[ЩNВџB‚€ЩYљ[™HQСWРSQУЉYЉH


YЉJЬYЩ\Ъ^™KLJIЉЉYЩ\Ъ^™KLJJJB‚ќ›ЪY
€[љЩ\—ЫњЧЩЬ[—Э[љ\]YJЫЫњЭЪ\Љ€\\‹ЫЫњЭЪ\Љ€[YKЫЫњЭЪ\Љ€]ЪЫ[YK[ќ›YЬКHВ€[ќYЩ\Ъ^™HHЩ]YЩ\Ъ^™J
NВ€Ъ\€]ќY–ФUУPVNВ€Э]XИZ[ќM—Э]ЪYВ€[ќ]ЪЩ™™X[Щ™В€Ъ^™WЭњЪ^™KЭ[Ъ^™NВ‚€Ыњљ[ќЉ]ќY‹UУPV‰\ЛЙ\И‹СPTђТФU[YJNВ€™X[Щ™HЬ[Љ]ќY‹ЧФ‘У“JNВ€YЉ™X[Щ™OHLJH™]\›€•SВ‚€В€ЭќXЭЭ]Ќ™X[ЬЭ]В€Y€
њЭ]Ќ
™X[Щ™	њ™X[ЬЭ]
JHЫЭИZ[Ь™X[В€њЪ^™HH™X[ЬЭ]њЭЬЪ^™NВ€Э[Ъ^™HHQСWРSQУЉњЪ^™JNВ€B‚€]ЪЩ™H
[ќ
HЮ\ШШ[
ЧУ”—ЫY[Y™ШЬ™X]K]ЪЫ[YKQ‘РУСVPКNВ€YЉ]ЪЩ™OHLJHВ€ЛИСО€\ЩHTЪ\™YY[[ЬћH\И[XЪВ€ЛИ“ХN€\ЩHYЩKX[YЫ™YЪ^™H
Э[Ъ^™JH›Ь€\ЪY[B€Ыњљ[ќЉ]ќY‹UУPV‰\ЛЙH”’]LM€€‹\\‹]ЪY
ККNВ€]ЪЩ™HЬ[Љ]ќY‹ЧРФ‘PUЧФ‘Ф‹ЧТT•TФ€ЧТUХTФЉNВ€B€YЉ]ЪЩ™OHLJHЫЭИZ[Ь™X[В‚€YЉќќ[Ш]MЌ
]ЪЩ™Э[Ъ^™JHOHLJHЫЭИZ[Ш›ЭВ‚€›ЫЫ]ЪЬ™\Э[H]ЪЩ[—ЬЫЫ[YJ]ЪЩ™™X[Щ™њЪ^™K]ЪЫ[YJNВ€ЫЬЩJ™X[Щ™
NВ€YЉ\]ЪЬ™\Э[
HВ€ЫЬЩJ]ЪЩ™
NВ€™]\›€•SВ€B‚€[™›ЪYЩ^[™›И^[™›ОВ€^[™›Л™›YЬИHS‘“ТQСVХTСWУђSQTФPСHS‘“ТQСVХTСWУP”ђT–WС‘В€^[™›Л›Xњ\ћWЩ™H]ЪЩ™В€^[™›Л›Xњ\ћWЫ[Y\ЬXЩHHљ]™\—Ы[Y\ЬXЩNВ€™]\›€[™›ЪYЩЬ[—Щ^
]ЪЫ[YK›YЬЛ	™^[™›КNВ‚€Z[Ш›Э‚€ЫЬЩJ]ЪЩ™
NВ€Z[Ь™X[‚€ЫЬЩJ™X[Щ™
NВ€™]\›€•SВџB‚‚‹К‚€
€]]™HQУЫЪИ[њЭ[][Ы€
љ^›Ь€ђШ[‰ЭX\ќY™™\‹Ь[™Ы\њ›Ь€ЉB€
‚€
€Т‘Ућ\\ЬЩ\ИH]K\ЪYH[љЩ\љЫЪИћHШ[[™ИYЫЩ]›ШРY™\ЬИ\™XЭB€
€њ›ЫH]]™HЫЩK€ЩH\ЩHћ]ZЫЪИИ[ќ\Щ\YЫЩ]›ШРY™\ЬИ]H]]™B€
€]™[ЫИ]ЫX\ќY™™\”[™ЩH[™™[]Yќ[Э[ЫњИ\™H™Y\™XЭYИЭ\‚€
€ЪYЭЛXќY™™\€[\[Y[ќ][ЫњЛ‚€
‚€
€\Иќ[Э[Ы€\ИШY™HИШ[][\H[Y\И8 %]\Щ\ИHЭX\™›YЛ‚€
‹В‚‹К€ћ]ZЫЪИ\\И
X]Ъ[™Ић]ZЫЪЛљYљ[љ][ЫњЛШYY[[ZXШ[JH
‹Вќ\YY€›ЪY
€ћ]ZЫЪЧЬЭX—ЭЫШШ[Вќ\YY€›ЪY

ћ]ZЫЪЧЪЫЪЩYЭЫШШ[
Jћ]ZЫЪЧЬЭX—ЭЫШШ[\ЪЧЬЭX‹[ќЭ]\ЧШЫЩK€ЫЫњЭЪ\€
Ш[\—Ь]Ы[YKЫЫњЭЪ\€
њЮ[WЫ[YK€›ЪY
›™]ЧЩќ[Л›ЪY
›™]ЧЩќ[ЧШ\™КNВќ\YY€ћ]ZЫЪЧЬЭX—ЭЫШШ[

ћ]ZЫЪЧЪЫЪЧШ[ЭЫШШ[
JJЫЫњЭЪ\€
Ш[YWЬ]Ы[YK€ЫЫњЭЪ\€
њЮ[WЫ[YK›ЪY
›™]ЧЩќ[Л€ћ]ZЫЪЧЪЫЪЩYЭЫШШ[ЫЪЩY›ЪY
љЫЪЩYШ\™КNВ‚€ЩYљ[™H’УSСWРUUУPUPИ€ЩYљ[™H’ФХUTЧРУСWУТИ‚‹К€[\ЬќYњ›ЫHЪ™ЫЩЬ[—ЪЫЪЛИ
›Ы‹\Э]XЛЫИЩHШ[€XШЩ\ЬИ]\™JH
‹В™^\›€›ЪY
€YЫЩ]›ШРY™\ЬЧЪЫЪКЫЫњЭЪ\Љ€›ШЫ[YJNВ‚њЭ]XИ›ЫЫYЫЪЫЪЧЪ[њЭ[YH[ЩNВ‚ќ›ЪY[њЭ[ЩЫШ[ЩYЫЪЫЪК›ЪY
HВ€YЉYЫЪЫЪЧЪ[њЭ[Y
H™]\›ЋВ€YЫЪЫЪЧЪ[њЭ[YHќYNВ‚€›ЪY
€ћ]ZЫЪЧЪ[™HHЬ[Љ›Xћ]ZЫЪЛњЫИ‹•У“ХКNВ€YЉћ]ZЫЪЧЪ[™HOH•S
HВ€ССJљ[њЭ[ЩЫШ[ЩYЫЪЫЪО€Z[YИШYXћ]ZЫЪЛњЫО€	\И‹\њ›ЬЉ
JNВ€™]\›ЋВ€B‚€ћ]ZЫЪЧЪЫЪЧШ[ЭЫШШ[ћ]ZЫЪЧЪЫЪЧШ[ЬВ€[ќ

ћ]ZЫЪЧЪ[љ]Ь
J[ќ[ЩK›ЫЫXќYКNВ‚€ћ]ZЫЪЧЪЫЪЧШ[ЬH
ћ]ZЫЪЧЪЫЪЧШ[ЭЫШШ[
HЮ[Jћ]ZЫЪЧЪ[™Kћ]ZЫЪЧЪЫЪЧШ[ЉNВ€ћ]ZЫЪЧЪ[љ]ЬH
[ќ

ЉJ[ќ›ЫЫ
JHЮ[Jћ]ZЫЪЧЪ[™Kћ]ZЫЪЧЪ[љ]ЉNВ‚€YЉћ]ZЫЪЧЪЫЪЧШ[ЬOH•Sћ]ZЫЪЧЪ[љ]ЬOH•S
HВ€ССJљ[њЭ[ЩЫШ[ЩYЫЪЫЪО€Z[YИљ[™ћ]ZЫЪЧЬЮ[X›ЫО€	\И‹\њ›ЬЉ
JNВ€ЫЬЩJћ]ZЫЪЧЪ[™JNВ€™]\›ЋВ€B‚€[ќљЫЪЧЬЭ]\ИHћ]ZЫЪЧЪ[љ]Ь
’УSСWРUUУPUPЛ[ЩJNВ€YЉљЫЪЧЬЭ]\ИOH’ФХUTЧРУСWУТКHВ€ћ]ZЫЪЧЬЭX—ЭЫШШ[ЭX€Hћ]ZЫЪЧЪЫЪЧШ[Ь
€•SК€Ш[YWЬ]Ы[YN€•SH[Xњ\љY\И
‹В€™YЫЩ]›ШРY™\ЬИ‹К€Ю[WЫ[YN€Hќ[Э[Ы€ИЫЪИ
‹В€
›ЪY
ЉHYЫЩ]›ШРY™\ЬЧЪЫЪЛК€™]ЧЩќ[О€Э\€™\XЩ[Y[ќ
‹В€•SК€ЫЪЩY€›ИШ[XЪИ™YYY
‹В€•SК€ЫЪЩYШ\™О€›ИШ[XЪИ\™И
‹В€
NВ€YЉЭX€OH•S
HВ€СТJљ[њЭ[ЩЫШ[ЩYЫЪЫЪО€ЭXШЩ\ЬЩќ[HЫЪЩYYЫЩ]›ШРY™\ЬИљXHћ]ZЫЪИЉNВ€H[ЩHВ€ССJљ[њЭ[ЩЫШ[ЩYЫЪЫЪО€ћ]ZЫЪЧЪЫЪЧШ[™]\›™Y•S›Ь€YЫЩ]›ШРY™\ЬИЉNВ€B€H[ЩHВ€ССJљ[њЭ[ЩЫШ[ЩYЫЪЫЪО€ћ]ZЫЪЧЪ[љ]Z[Y
	Y
H‹љЫЪЧЬЭ]\КNВ€ЫЬЩJћ]ZЫЪЧЪ[™JNВ€BџB