# إصدار Android الموقّع

يحتوي هذا المشروع على Workflow باسم `Android Release` يبني تلقائيًا ملفي APK وAAB من فرع `main`، كما يمكن تشغيله يدويًا من تبويب **Actions**. يخرج ملف AAB الموقّع للرفع إلى Google Play، ويخرج APK الموقّع للاختبار أو التوزيع المباشر.

> **مهم:** لا تضع ملف keystore أو كلمات المرور داخل Git أو داخل ملفات المشروع. فقدان مفتاح الرفع قد يمنع تحديث التطبيق المنشور، لذلك احتفظ بنسخة احتياطية آمنة خارج GitHub أيضًا.

## 1. إنشاء مفتاح الرفع

إذا كان التطبيق منشورًا من قبل، استخدم **مفتاح الرفع الحالي نفسه** ولا تنشئ مفتاحًا جديدًا. أمّا إذا لم يكن لديك مفتاح رفع، فأنشئه محليًا من جذر المشروع:

```bash
chmod +x scripts/create-upload-keystore.sh
./scripts/create-upload-keystore.sh "$HOME/win-rah-train-upload.jks"
```

سيطلب السكربت كلمتي مرور ويستخدم الاسم المستعار `upload` افتراضيًا. يمكن تغيير الاسم المستعار هكذا:

```bash
KEY_ALIAS=my-upload ./scripts/create-upload-keystore.sh "$HOME/win-rah-train-upload.jks"
```

## 2. إضافة GitHub Secrets

افتح صفحة الريبو ثم اذهب إلى **Settings → Secrets and variables → Actions → New repository secret**. أضف الأسرار التالية بالأسماء الدقيقة:

| الاسم | القيمة |
| --- | --- |
| `KEYSTORE_BASE64` | محتوى keystore بعد تحويله إلى Base64 من جهازك المحلي |
| `STORE_PASSWORD` | كلمة مرور keystore |
| `KEY_ALIAS` | الاسم المستعار للمفتاح، وغالبًا `upload` |
| `KEY_PASSWORD` | كلمة مرور المفتاح |

لتحويل keystore إلى قيمة Base64، نفّذ الأمر التالي محليًا ثم انسخ الناتج كاملًا إلى Secret باسم `KEYSTORE_BASE64`:

```bash
base64 -w 0 "$HOME/win-rah-train-upload.jks"
printf '\n'
```

لا تضع كلمات المرور في الأوامر أو في ملفات يتم رفعها إلى Git. لا يحتاج Workflow إلى `GEMINI_API_KEY` كي يبني التطبيق؛ ذلك السر يخص تشغيل ميزات Gemini داخل التطبيق، ويمكن إضافته لاحقًا بالطريقة المناسبة إن كانت الخدمة مفعّلة.

## 3. تشغيل البناء

يدخل Workflow في العمل تلقائيًا عند كل push إلى `main`. كما يمكن تشغيله يدويًا من **Actions → Android Release → Run workflow**. عند التشغيل اليدوي، يمكن إدخال `version_name` مثل `1.0.1`؛ أما `versionCode` فيُحدَّد تلقائيًا من رقم تشغيل GitHub Actions لضمان زيادته بين الإصدارات.

بعد انتهاء المهمة، افتح قسم **Artifacts** في صفحة تشغيل Workflow وحمّل الأرشيف الذي يحتوي على:

```text
app-release.apk
app-release.aab
```

ارفع ملف `app-release.aab` إلى Google Play Console. استخدم ملف APK للاختبار على الأجهزة أو للتوزيع خارج Google Play.

## 4. اختبار محلي اختياري

لاختبار إصدار Release محليًا، عرّف المتغيرات التالية في جلسة الطرفية فقط:

```bash
export KEYSTORE_PATH="$HOME/win-rah-train-upload.jks"
export STORE_PASSWORD='ضع-كلمة-مرور-keystore-هنا'
export KEY_ALIAS='upload'
export KEY_PASSWORD='ضع-كلمة-مرور-key-هنا'
export VERSION_CODE=1
export VERSION_NAME='1.0.0'
./gradlew assembleRelease bundleRelease
```

ستجد الملفات الناتجة هنا:

```text
app/build/outputs/apk/release/app-release.apk
app/build/outputs/bundle/release/app-release.aab
```

للتحقق من توقيع APK:

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

## 5. ملاحظات Google Play

يجب أن يكون `applicationId` ثابتًا بعد نشر التطبيق. في هذا المشروع هو:

```text
com.aistudio.trainradar.dzxyz
```

لا تغيّره إذا أنشأت التطبيق بهذا المعرّف في Google Play Console. كذلك يجب أن يكون `versionCode` أعلى من آخر إصدار مرفوع، وهو ما يعالجه Workflow تلقائيًا باستخدام رقم التشغيل.

## الملفات التي أضيفت

| الملف | الغرض |
| --- | --- |
| `.github/workflows/android-release.yml` | بناء APK وAAB موقّعين تلقائيًا ورفعهما كـ Artifacts |
| `scripts/create-upload-keystore.sh` | إنشاء مفتاح Upload جديد دون الكتابة فوق مفتاح موجود |
| `gradlew` و`gradle/wrapper/` | تثبيت نسخة Gradle القابلة لإعادة الإنتاج على CI |
| `docs/ANDROID_RELEASE.md` | توثيق إعداد المفتاح والأسرار والتشغيل |
