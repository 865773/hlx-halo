#!/system/bin/sh
set -e
B=/data/local/tmp/build_hlx
AB=/data/local/tmp/alpine/build_hlx
AAPT=/data/local/tmp/sdk/build-tools/aapt
ZIPALIGN=/data/local/tmp/sdk/build-tools/zipalign
JDK17=/usr/lib/jvm/java-17-openjdk/bin/java
ALPINE=/data/local/tmp/alpine

mkdir -p "$AB/src/dudu/hlx/halo" "$AB/res" "$AB/assets" "$AB/out" "$AB/classes" "$AB/dex"
cp -f "$B/src/dudu/hlx/halo/"*.java "$AB/src/dudu/hlx/halo/"
cp -f "$B/AndroidManifest.xml" "$AB/"
cp -rf "$B/res/"* "$AB/res/"
cp -rf "$B/assets/"* "$AB/assets/"
cp -f "$B/android.jar" "$B/r8.jar" "$B/key.jks" "$AB/"

cd "$AB"
rm -rf out classes dex
mkdir -p out classes dex

echo "== [1/6] aapt =="
$AAPT package -f -M "$AB/AndroidManifest.xml" -S "$AB/res" -A "$AB/assets" -I "$AB/android.jar" -F "$AB/out/unsigned.apk"

echo "== [2/6] javac =="
chroot "$ALPINE" /bin/sh -c 'export PATH=/bin:/sbin:/usr/bin:/usr/sbin; export LD_LIBRARY_PATH=/usr/lib/jvm/java-17-openjdk/lib:/usr/lib/jvm/java-17-openjdk/lib/jli:/usr/lib:/lib; cd /build_hlx; '"$JDK17"' --add-modules jdk.compiler -m jdk.compiler/com.sun.tools.javac.Main -source 1.8 -target 1.8 -nowarn -cp android.jar -d classes src/dudu/hlx/halo/*.java'

echo "== [3/6] d8 =="
chroot "$ALPINE" /bin/sh -c 'export PATH=/bin:/sbin:/usr/bin:/usr/sbin; export LD_LIBRARY_PATH=/usr/lib/jvm/java-17-openjdk/lib:/usr/lib/jvm/java-17-openjdk/lib/jli:/usr/lib:/lib; cd /build_hlx; '"$JDK17"' -cp r8.jar com.android.tools.r8.D8 --min-api 21 --lib android.jar --output dex classes/dudu/hlx/halo/*.class'

echo "== [4/6] jar =="
chroot "$ALPINE" /bin/sh -c 'export PATH=/bin:/sbin:/usr/bin:/usr/sbin; export LD_LIBRARY_PATH=/usr/lib/jvm/java-17-openjdk/lib:/usr/lib/jvm/java-17-openjdk/lib/jli:/usr/lib:/lib; cd /build_hlx/dex; '"$JDK17"' --add-modules jdk.jartool -m jdk.jartool/sun.tools.jar.Main uf /build_hlx/out/unsigned.apk classes.dex'

echo "== [5/6] sign =="
chroot "$ALPINE" /bin/sh -c 'export PATH=/bin:/sbin:/usr/bin:/usr/sbin; export LD_LIBRARY_PATH=/usr/lib/jvm/java-17-openjdk/lib:/usr/lib/jvm/java-17-openjdk/lib/jli:/usr/lib:/lib; '"$JDK17"' --add-modules jdk.jartool -m jdk.jartool/sun.security.tools.jarsigner.Main -keystore /build_hlx/key.jks -storepass 123456 -keypass 123456 -sigalg SHA256withRSA -digestalg SHA-256 -signedjar /build_hlx/out/signed.apk /build_hlx/out/unsigned.apk ljs'

echo "== [6/6] zipalign =="
$ZIPALIGN -f 4 "$AB/out/signed.apk" "$AB/out/final.apk"
echo "构建完成：$AB/out/final.apk"
