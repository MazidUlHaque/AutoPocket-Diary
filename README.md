# AutoPocket Diary

**Your personal diary of money coming in and going out, built from your bank and mobile-wallet SMS.**
Free. Works fully offline. Your data never leaves your phone.

Created by **Md. Mazid-Ul-Haque**. © 2026, all rights reserved (see [LICENSE](LICENSE)).

**[Download the latest version](https://github.com/MazidUlHaque/AutoPocket-Diary/releases/latest)**. This is the only official download.

[বাংলায় পড়ুন ↓](#বাংলায়)

---

## What it does

- Reads transaction SMS from bKash, Nagad, Rocket, Upay and Bangladeshi banks: salary, transfers, card purchases, ATM withdrawals, bills, DPS, insurance premiums, interest, charges and more.
- **Accounts and cards:** finds each account and card in your SMS (e.g. DBBL ••1234, Visa ••1111) and asks once what it is, so you see totals per account and per card.
- **Incoming and outgoing:** salary, bonus and interest shown separately; DPS, insurance and investments shown as "savings" inside outgoing.
- **Own transfers aren't counted twice:** bank → bank, card → bKash, credit card bill payments. When the app isn't sure, it asks you in "Needs a quick look".
- Ignores OTPs, offers, statements, failed transactions and duplicate SMS.
- Smart categories (Bata → Clothing & Shoes, Shwapno → Groceries, DESCO → Electricity, …) that learn from your changes.
- Any period: week, month, 2 months, year, or your own dates.
- Monthly budgets with warnings, a home-screen widget, a password-locked **hidden zone**, and backups saved on your phone.
- বাংলা and English, light and dark.

## Your privacy

- **Everything stays on your phone.** The app has **no internet permission**, so it *cannot* send your data to the developer or anyone else. Every build is checked automatically to keep it that way.
- Data is stored **encrypted** and is **not** copied to Google backup.
- No ads, no accounts, no sign-up.
- SMS that aren't bank or wallet transactions are skipped and never stored.

## Two editions: which one?

| | **AutoPocket Diary** (recommended) | **AutoPocket Diary Auto** |
|---|---|---|
| File | `AutoPocketDiary.apk` | `AutoPocketDiary-Auto.apk` |
| How SMS get in | **Share** an SMS to the app, **paste** SMS, **import** an SMS backup file, or **automatic daily import** from the free "SMS Backup & Restore" app | Reads bank and wallet SMS from your inbox **automatically** |
| SMS permission | **None** | Read SMS |
| Installing | Installs normally | Google Play Protect blocks it in some countries (steps below) |

Everything else is the same in both. They can be installed side by side; each keeps its own data.

**Why is the Auto edition blocked?** In several countries Google Play Protect blocks *every* app downloaded from a browser that asks to read SMS, because scam apps misuse SMS to steal OTPs. It's a blanket rule, not a judgement of this app.

## How to install

1. On your Android phone, open the **[download page](https://github.com/MazidUlHaque/AutoPocket-Diary/releases/latest)** and tap **AutoPocketDiary.apk** (or **AutoPocketDiary-Auto.apk**).
2. Open the downloaded file.
3. If Android says **"your phone is not allowed to install unknown apps from this source"**, tap **Settings**, turn on **Allow from this source**, and go back.
4. If **Google Play Protect** shows a warning with **More details → Install anyway**, you may use it. Only do this for the file from the official page.
5. Open the app and read the welcome screens.

**Updating:** download the new APK and install it over the old one. Your data is kept.

**Check it's genuine (optional):** in the app open **More → About & privacy**. It should say "Official version", and its signing fingerprint should match the one on the download page.

## How to give SMS access

### Standard edition (no permission needed)
Bring SMS in any of these ways (**More → Add from SMS**):
- **Share:** in your Messages app, touch and hold a bank or bKash SMS → **Share** → **AutoPocket Diary**.
- **Paste:** copy one or more SMS and paste them into the app.
- **Import a file:** an SMS backup file (`.xml`) from the free app *SMS Backup & Restore*.
- **Automatic (recommended):** in *SMS Backup & Restore*, set up a **daily** backup of **messages only** to a **folder on your phone** (not Google Drive). Then in AutoPocket Diary choose that folder once. Every time you open the app, it reads the newest backup.

### Auto edition (three one-time steps)
1. **Installing, if Play Protect says "App blocked to protect your device":** open the **Play Store** → your profile picture → **Play Protect** → ⚙ → turn off **Scan apps with Play Protect**. Install the app, then **turn scanning back on**.
2. **Allow restricted settings:** open the app and tap **Allow SMS access**. If Android says the app was denied access, open phone **Settings → Apps → AutoPocket Diary Auto**, tap **⋮** (top-right) → **Allow restricted settings**, and confirm with your phone lock.
3. **Allow SMS:** on the same page tap **Permissions → SMS → Allow**.

The app has a guide with buttons for these steps: **More → SMS access**.

If your phone never shows "Allow restricted settings", use the standard edition instead.

## Donations

The app is completely free. If you'd like, you can send a donation (minimum ৳10) by bKash Send Money; details are in the app under **More → Donate**. Donations are used by the developer for charity work, such as casual donations to people in need. The app never takes money itself; donating is entirely your choice.

**The developer will never call you or ask for your PIN, OTP or password.**

## Limits

- It only sees transactions that sent an SMS (and, in the standard edition, the SMS you bring in).
- It may sometimes misread an SMS; you can edit or delete any entry.
- It's a personal record, not financial advice. Not affiliated with bKash, Nagad, Rocket, Upay or any bank.

## Fonts

The app includes [Plus Jakarta Sans](https://fonts.google.com/specimen/Plus+Jakarta+Sans) and [Hind Siliguri](https://fonts.google.com/specimen/Hind+Siliguri), both under the SIL Open Font License (copies in `app/src/main/assets/licenses`).

---

## বাংলায়

**AutoPocket Diary** আপনার ব্যাংক ও বিকাশ/নগদ/রকেট-এর SMS থেকে কত টাকা এলো আর কত গেল তার হিসাব দেখায়: প্রতিটি অ্যাকাউন্ট ও কার্ড আলাদা করে এবং মোট। বেতন, বোনাস ও সুদ আলাদা দেখায়; ডিপিএস, বিমা ও বিনিয়োগ "সঞ্চয়" হিসেবে দেখায়; নিজের এক অ্যাকাউন্ট থেকে আরেক অ্যাকাউন্টে টাকা সরালে তা দুবার গোনে না।

**গোপনীয়তা:** সব তথ্য শুধু আপনার ফোনেই থাকে। অ্যাপটির ইন্টারনেট ব্যবহারের অনুমতি নেই, তাই এটি আপনার কোনো তথ্য ডেভেলপার বা অন্য কারও কাছে পাঠাতে পারে না। তথ্য ফোনে এনক্রিপ্ট করে রাখা হয়।

**দুটি সংস্করণ:**
- **AutoPocket Diary** (`AutoPocketDiary.apk`, সবার জন্য): স্বাভাবিকভাবে ইনস্টল হয়, SMS পড়ার কোনো অনুমতি লাগে না।
- **AutoPocket Diary Auto** (`AutoPocketDiary-Auto.apk`): নিজে থেকে SMS পড়ে, তবে ইনস্টল ও অনুমতিতে কয়েকটি বাড়তি ধাপ লাগে।

**ইনস্টল করার নিয়ম:**
1. ফোনে **[ডাউনলোড পেজ](https://github.com/MazidUlHaque/AutoPocket-Diary/releases/latest)** খুলে APK ডাউনলোড করুন।
2. ফাইলটি খুলুন। "unknown apps" অনুমতি চাইলে **Settings → Allow from this source** চালু করুন।
3. Play Protect সতর্ক করলে **More details → Install anyway** চাপুন (শুধু অফিসিয়াল পেজের ফাইলের জন্য)।

**SMS কীভাবে যোগ হবে:**
- **সাধারণ সংস্করণ:** মেসেজ অ্যাপে ব্যাংক বা বিকাশের SMS চেপে ধরে **Share → AutoPocket Diary**; অথবা SMS পেস্ট করুন; অথবা "SMS Backup & Restore" অ্যাপ দিয়ে প্রতিদিন ফোনের একটি ফোল্ডারে ব্যাকআপ রাখুন এবং অ্যাপে সেই ফোল্ডারটি একবার বেছে দিন, তাহলে অ্যাপ খুললেই নতুন লেনদেন যোগ হবে।
- **Auto সংস্করণ:**
  1. Play Protect "App blocked" দেখালে: Play Store → প্রোফাইল → Play Protect → ⚙ → "Scan apps with Play Protect" কিছুক্ষণের জন্য বন্ধ করে ইনস্টল করুন, তারপর **আবার চালু করুন**।
  2. অ্যাপে **SMS পড়ার অনুমতি দিন** চাপুন। অনুমতি আটকে গেলে: ফোনের **Settings → Apps → AutoPocket Diary Auto → ⋮ → Allow restricted settings**।
  3. একই পাতায় **Permissions → SMS → Allow**।
  অ্যাপের ভেতরে **আরও → SMS পড়ার অনুমতি**-তে বোতামসহ এই নির্দেশনা আছে।

**অনুদান:** অ্যাপটি সম্পূর্ণ ফ্রি। চাইলে বিকাশে অনুদান পাঠাতে পারেন (ন্যূনতম ৳১০), যা ডেভেলপার দাতব্য কাজে ব্যবহার করেন। অ্যাপ নিজে কখনো টাকা কাটে না। **ডেভেলপার কখনো ফোন করে আপনার PIN বা OTP চাইবেন না।**
