#!/usr/bin/env bash
# Runs inside the emulator after emulator-check.sh: loads sample stories into a debug build, walks through
# the main screens with taps found from the screen's own accessibility tree, and records a video.
set -u
PKG=com.akis.app
OUT=emulator-report/tour
mkdir -p "$OUT"

adb uninstall "$PKG" > /dev/null 2>&1
adb install -r app/build/outputs/apk/debug/app-debug.apk > "$OUT/install.txt" 2>&1

NOW=$(date +%s)000
python3 - "$NOW" <<'PY' > "$OUT/akis.json"
import json, sys
now = int(sys.argv[1])
feeds = [
    {"id": "f1", "url": "https://haber.example/rss", "title": "Örnek Haber", "lastFetchedMillis": now, "group": "Haber"},
    {"id": "f2", "url": "https://teknoloji.example/rss", "title": "Örnek Teknoloji", "lastFetchedMillis": now, "group": "Teknoloji"},
]
titles = [
    ("f2", "Android 17 için uygulama bellek sınırları cihazın RAM miktarına göre belirlenecek"),
    ("f1", "Merkez Bankası faiz kararını açıkladı: piyasalar nasıl tepki verdi?"),
    ("f2", "Yeni nesil işlemciler enerji verimliliğinde çıtayı yükseltiyor"),
    ("f1", "Şehir içi ulaşımda yeni dönem: aktarma ücretleri düşüyor"),
    ("f2", "Telefon pilleri için yeni nesil kimya: yüzde otuz daha uzun ömür"),
    ("f1", "Hafta sonu hava durumu: yağışlar bölgeye göre değişecek"),
    ("f2", "Açık kaynak projeler bakımcı sıkıntısıyla karşı karşıya"),
    ("f1", "Üniversite sınavında yeni düzenleme için takvim belli oldu"),
]
body = " ".join(["Bu, örnek bir haber metnidir ve okuyucunun satır aralığını, yazı boyutunu ve kenar boşluklarını görmek için yazılmıştır."] * 6)
articles = []
for i, (feed, title) in enumerate(titles):
    articles.append({
        "id": "a%d" % i, "feedId": feed, "title": title, "link": "https://example.com/%d" % i,
        "text": body + "\n\n" + body, "publishedMillis": now - (i + 1) * 20 * 60 * 1000,
    })
print(json.dumps({"feeds": feeds, "articles": articles, "lastRefreshMillis": now}, ensure_ascii=False))
PY

adb shell am force-stop "$PKG"
adb push "$OUT/akis.json" /data/local/tmp/akis.json > /dev/null
adb shell "run-as $PKG mkdir -p files && run-as $PKG sh -c 'cat /data/local/tmp/akis.json > files/akis.json'" > "$OUT/seed.txt" 2>&1

# Finds a node by text or content-desc in the screen's accessibility tree and taps its centre.
tap() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null 2>&1
  local xy
  xy=$(python3 - "$1" "$OUT/ui.xml" <<'PY'
import re, sys
want, path = sys.argv[1], sys.argv[2]
xml = open(path, encoding="utf-8").read()
for m in re.finditer(r'<node [^>]*>', xml):
    node = m.group(0)
    text = re.search(r' text="([^"]*)"', node)
    desc = re.search(r' content-desc="([^"]*)"', node)
    if (text and want in text.group(1)) or (desc and want in desc.group(1)):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
)
  if [ -n "$xy" ]; then adb shell input tap $xy; echo "tap '$1' at $xy" >> "$OUT/steps.txt"; else echo "NOT FOUND '$1'" >> "$OUT/steps.txt"; fi
  sleep 2
}
shot() { sleep 1; adb exec-out screencap -p > "$OUT/$1.png"; }

adb shell screenrecord --time-limit 150 /sdcard/tour.mp4 &
REC=$!
sleep 2
adb shell am start -n "$PKG/.MainActivity" > /dev/null
sleep 6
shot 01-home

tap "Ara"; shot 02-search-empty
adb shell input text "android"; sleep 2; shot 03-search-results
adb shell input keyevent 4; sleep 1; adb shell input keyevent 4; sleep 2

tap "Ayarlar"; shot 04-settings
adb shell input swipe 540 1800 540 600 400; sleep 1; shot 05-settings-scrolled
adb shell input keyevent 4; sleep 2

tap "Kaynaklar"; shot 06-sources
adb shell input keyevent 4; sleep 2

tap "Android 17"; sleep 3; shot 07-reader
adb shell input swipe 540 1800 540 600 400; sleep 1; shot 08-reader-scrolled
adb shell input keyevent 4; sleep 2
shot 09-back-home

wait "$REC" 2>/dev/null || true
sleep 3
adb pull /sdcard/tour.mp4 "$OUT/tour.mp4" > /dev/null 2>&1
adb logcat -d -s AndroidRuntime:E > "$OUT/crashes.txt" 2>&1
adb shell pidof "$PKG" > "$OUT/alive.txt" 2>&1 || echo "NOT RUNNING" >> "$OUT/alive.txt"
cat "$OUT/steps.txt" "$OUT/alive.txt" "$OUT/crashes.txt"
