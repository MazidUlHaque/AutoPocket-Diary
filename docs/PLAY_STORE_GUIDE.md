# Publishing AutoPocket Diary on Google Play

This guide takes you step by step from "no developer account" to "live on Play". Allow **3 to 5 weeks** in total; most of that is waiting for Google.

**What's already prepared in this repo:**

| What | Where |
|---|---|
| Play edition of the app (auto SMS reading, no donate, separate package `com.mazidulhaque.autopocketdiary`) | `play` flavor in `app/build.gradle.kts` |
| Upload file (`.aab`) | Built by GitHub Actions on every push to `main`: artifact **AutoPocketDiary-Play-bundle** |
| Privacy policy | <https://github.com/MazidUlHaque/AutoPocket-Diary/blob/main/docs/PRIVACY.md> |
| App icon 512×512, feature graphic 1024×500 | `docs/play-store/` |
| Phone screenshots (status bar and account numbers hidden) | `docs/screenshots/` |
| Prominent disclosure screen before the SMS permission | Built into the app |

---

## Step 1: Create a developer account (one time, US$25)

1. Go to <https://play.google.com/console/signup> and sign in with the Google account you want to own the app. Use one you'll keep for years.
2. Choose **Yourself** (personal account).
3. Pay the **US$25** one-time fee. You need a Visa or Mastercard that works internationally; many Bangladeshi bank debit and credit cards work once international/e-commerce use is turned on with your bank.
4. Fill in your legal name and address exactly as on your **NID or passport**, then complete **identity verification** (upload the ID). This can take a few days.
5. Verify your **phone number** and **email**. You'll also be asked to verify that you have access to a real Android phone, using the Play Console app.
6. Pick a **developer name** shown on the store, for example *Mazid Ul Haque*. Add a **contact email** that users will see. You can create a separate Gmail for this.

## Step 2: Create the app

In Play Console, choose **Home → Create app**:

| Field | Answer |
|---|---|
| App name | `AutoPocket Diary` |
| Default language | English (United Kingdom) or English (United States) |
| App or game | App |
| Free or paid | **Free** (this cannot be changed to paid later) |
| Declarations | Tick both (Developer Programme Policies, US export laws) |

The package name is set by the first upload: `com.mazidulhaque.autopocketdiary`. It can never change.

## Step 3: Get the upload file (.aab)

1. Open <https://github.com/MazidUlHaque/AutoPocket-Diary/actions> and click the newest green run on `main`.
2. Scroll to **Artifacts** and download **AutoPocketDiary-Play-bundle**. Unzip it to get `AutoPocketDiary-Play.aab`.
   - Artifacts are deleted after 90 days. To make a fresh one, re-run the workflow (Actions → *Build APK* → **Run workflow**) or push any change.
3. Each build has a higher version code (the run number), which Play requires for every new upload.

The `.aab` is **not** put on the public download page, since it's only for Play.

### About signing (important)

On the first upload, Play asks about **Play App Signing**. Choose the default, **Let Google manage and protect your app signing key**.

- Google signs the app users download. The key in this repo becomes your **upload key**: Play only accepts updates signed with it.
- Keep the **`SIGNING_PASSWORD`** repository secret and your own copy of the password safe. If it is ever lost, you can ask Google to reset the upload key (Play Console → Test and release → App integrity). It is not the end of the app, but it takes time.
- Because Google re-signs it, the Play app is meant to show "Installed from Google Play" under **More → About & privacy**, not the GitHub fingerprint.

## Step 4: Set up the store listing

Play Console → **Grow users → Store presence → Main store listing**.

**App name (max 30):**
```
AutoPocket Diary
```

**Short description (max 80):**
```
Money diary that fills itself in from your bank & bKash SMS. Offline & private.
```

**Full description (max 4000):**
```
Most expense trackers make you type every purchase. AutoPocket Diary doesn't.

It reads the transaction SMS your bank and mobile wallet already send you (DBBL, IFIC, City, BRAC, EBL, bKash, Nagad and more), and does the bookkeeping for you:

• Money in and money out, sorted by day, week, month or any range
• Every account and card found automatically, each with its own history
• Shops and bills put in the right category (Shwapno → groceries, DESCO → electricity)
• Moving money between your own accounts is recognised and not counted twice
• Salary, bonus and interest shown separately; DPS, insurance and investments shown as savings
• OTPs, offers, failed payments and duplicate SMS are ignored
• Credit card spending and bill payments tracked correctly
• It only asks you when it truly can't tell

ALSO INCLUDED
• Monthly budgets with warnings
• Insights: where your money went
• Hidden zone: private entries locked with PIN or fingerprint
• Locked backup file and spreadsheet (CSV) export
• Home-screen widget
• Bangla and English, light and dark mode
• Add cash spending by hand when there's no SMS

PRIVATE BY DESIGN
• Works fully offline: the app has no internet permission at all
• Nothing is uploaded; no account, no sign-up, no ads
• Your data is stored encrypted on your phone
• Only bank and wallet transaction SMS are read; personal messages and OTPs are skipped and never stored

The developer will never call you or ask for your PIN, OTP or password.
AutoPocket Diary is a personal record, not financial advice, and is not affiliated with any bank or wallet.
```

**Graphics:**

| Asset | File |
|---|---|
| App icon (512×512 PNG) | `docs/play-store/icon-512.png` |
| Feature graphic (1024×500) | `docs/play-store/feature-graphic.png` |
| Phone screenshots (2 to 8) | `docs/screenshots/home.png`, `accounts.png`, `insights.png`, `new-account.png`, `home-bangla.png`, `more.png` |

Then, under **Store settings**:

- **Category:** Finance
- **Tags:** Budget, Expense tracker, Personal finance
- **Contact details:** your contact email. Website (optional): `https://github.com/MazidUlHaque/AutoPocket-Diary`

## Step 5: Fill in "App content"

Play Console → **Policy and programmes → App content**. Answer each item:

| Section | Answer |
|---|---|
| **Privacy policy** | `https://github.com/MazidUlHaque/AutoPocket-Diary/blob/main/docs/PRIVACY.md` |
| **App access** | *All functionality is available without special access* (no login) |
| **Ads** | *No, my app does not contain ads* |
| **Content rating** | Start the questionnaire, category **"All other app types"**, answer **No** to everything (no violence, gambling, user chat, etc.). Expected result: Everyone / 3+ |
| **Target audience** | **18 and over** only. "Could the app unintentionally appeal to children?" → No |
| **News app** | No |
| **COVID-19 / health** | No / not a health app |
| **Government app** | No |
| **Financial features** | Choose **"Money management / budgeting / personal finance tracking"** (exact wording may vary). It does **not** provide loans, payments, banking, crypto or trading |
| **Data safety** | See Step 6 |
| **Sensitive permissions (SMS)** | See Step 7 |

## Step 6: Data safety form

Google counts data as "collected" only when it **leaves the device**. AutoPocket Diary has no internet permission, so:

1. *Does your app collect or share any of the required user data types?* → **No**
2. That's it. The form then shows **"No data collected"** and **"No data shared with third parties"**.
3. If it asks about encryption in transit or deletion requests, those don't apply, since nothing is transmitted.

## Step 7: SMS permission declaration (the important one)

Google only allows `READ_SMS` for a few uses. **"SMS-based money management"** is one of the allowed exceptions, and this is exactly what the app does. You'll fill in the **Permissions Declaration Form** when you upload the first `.aab` (Play Console prompts you), or under App content → *Sensitive permissions*.

| Question | Answer |
|---|---|
| Core functionality | **SMS-based money management** |
| Describe how your app uses the permission | Paste the text below |
| Video | Upload a short screen recording (YouTube unlisted or Google Drive link) |

**Description to paste:**
```
AutoPocket Diary is a personal money manager whose core feature is automatic expense and income tracking from bank and mobile-wallet transaction SMS.

With the user's permission, the app reads SMS in the inbox, recognises transaction messages from banks and wallets (e.g. "Tk 1,200 debited from A/C xx1234"), and records the amount, date, account and merchant so the user's income, spending, accounts, budgets and insights are kept up to date without manual entry.

Before asking for the permission, the app shows a prominent in-app disclosure explaining what is read and why. Messages that are not financial transactions (personal messages, OTPs, offers) are ignored and never stored.

All processing happens on the device. The app has no INTERNET permission, so SMS data can never leave the phone. Data is stored in an encrypted local database and excluded from cloud backup. If the user declines the permission, they can still share or paste individual SMS into the app.
```

**Video script (30 to 60 seconds, record on your phone with the Play build):**

1. Open the app fresh. Show the welcome screens briefly.
2. Tap the centre **SMS** button → **Turn on SMS access**. The disclosure dialog appears; pause on it for 3 seconds so the reviewer can read it.
3. Tap **Continue** → Android's permission prompt → **Allow**.
4. Show the app scanning and the Home screen filling with transactions, accounts and categories.
5. Open one transaction to show it came from a bank SMS.
6. Open **Insights** and **Accounts** to show money management built on SMS.
7. Optional: show **More → About & privacy**, which says the app works offline with no internet permission.

Use a phone without personal SMS on screen, or hide account digits. Reviewers only need to see the flow.

If Google rejects the declaration, the reply usually says why. Common fixes are a clearer video or a more explicit description. You can appeal or resubmit.

## Step 8: Closed testing with 12 testers for 14 days (required for new personal accounts)

New personal developer accounts must run a **closed test with at least 12 testers who stay opted in for 14 days in a row** before they can apply for production.

1. Play Console → **Test and release → Testing → Closed testing → Create track** (or use the default "Alpha").
2. **Countries:** Bangladesh (add others if you like).
3. **Testers:** create an email list with at least 12 Gmail addresses. Ask 15 to 20 friends and family to be safe, since some forget.
4. **Create release** → upload `AutoPocketDiary-Play.aab` → release name e.g. `1.0.42` → release notes → **Save → Review → Start rollout**.
5. The first review takes from a few hours to about 7 days.
6. Copy the **opt-in link** from the track page and send it to your testers. Each tester must:
   - open the link while signed in with the Gmail you added,
   - tap **Become a tester**,
   - install the app from Play,
   - **keep it installed and stay opted in for at least 14 days**, and open it now and then.
7. Push updates during the test if you like (upload a newer `.aab` to the same track). Activity helps your production application.

## Step 9: Apply for production

After 14 days, go to **Dashboard → Apply for production**. Google asks a few questions:

- how you recruited testers (friends, family, colleagues),
- what feedback you got and what you changed,
- why the app is ready (e.g. "tested with N users on many bank SMS formats; fixed parsing issues").

Answer honestly in plain words. Approval usually takes up to 7 days.

## Step 10: Release to production

1. **Test and release → Production → Create release**. Upload the latest `.aab`, or promote the closed-test release.
2. Countries: Bangladesh (or more).
3. Save → Review → **Start rollout to Production**.
4. After review, the app appears on Play. Search may take a day or two to find it.

Then update the "Google Play edition" note in the README with the Play link.

## Updating the app later

1. Push changes to `main`. GitHub Actions builds a new `.aab` with a higher version code.
2. Download **AutoPocketDiary-Play-bundle** from that run.
3. Play Console → Production → **Create release** → upload → release notes → rollout.

The GitHub APKs (`AutoPocketDiary.apk`, `AutoPocketDiary-Auto.apk`) keep updating as before. The Play app uses a different package name and signature, so it installs **alongside** the GitHub versions, not over them. Users who move to the Play version can carry their data with a backup file: **More → Backup** in the old app, then restore in the Play app.

## Official references

- Use of SMS or Call Log permission groups: <https://support.google.com/googleplay/android-developer/answer/10208820>
- Testing requirements for new personal developer accounts: <https://support.google.com/googleplay/android-developer/answer/14151465>
- Prominent disclosure and consent: <https://support.google.com/googleplay/android-developer/answer/11150561>
- Data safety section: <https://support.google.com/googleplay/android-developer/answer/10787469>
- Play App Signing: <https://support.google.com/googleplay/android-developer/answer/9842756>
