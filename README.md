# OpenShorts for Android

**AI Clip Studio & UGC Video Creator** built with Kotlin, Jetpack Compose, Material Design 3, Media3 ExoPlayer, Firebase Authentication + Cloud Firestore, and Firebase AI Logic (`gemini-3.8-flash` & `gemini-nano-banana-2.1`).

## Features
- **Google Sign-In & Cloud Sync**: Secure Credential Manager authentication (`GetSignInWithGoogleOption`) with per-user Firestore persistence.
- **Clip Generator**: Pick device videos via Android Photo Picker or enter a URL to detect viral 9:16 short moments, preview with live hook & animated subtitle overlays on Media3 ExoPlayer, customize subtitle styling, edit viral hooks, localize into 30+ languages, and publish or share.
- **AI Shorts UGC Creator**: 5-step wizard to research products, generate conversion scripts across 5 UGC formats, synthesize AI presenter portraits (`gemini-nano-banana-2.1`), and save storyboards.
- **UGC Studio Gallery**: Manage saved AI Shorts campaigns, studio starter presenters, and custom AI actor portraits.
- **YouTube Studio**: Analyze videos or topics for 10 viral CTR titles (<70 chars), refine titles with Gemini multi-turn chat, generate 16:9 AI thumbnails with optional face/background reference photos, write chaptered SEO descriptions, and export.
- **Settings**: Manage Google session, inspect Firebase AI Logic status, and optionally configure Upload-Post, ElevenLabs, or fal.ai keys in DataStore.
