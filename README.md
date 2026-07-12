# SoundTune

![SoundTune](doc/ed57005e16c2455fab4dd6fbe21a6927.png)

車のDSP・イコライザー調整のための基準音源を生成するAndroidアプリ。  
任意の周波数の正弦波を最大5音同時にリアルタイム再生し、出力デバイスの検知を行います。

---

## 背景

車のDSP設定をしたところ、曲によって音のばらつきが大きく、低音が響くJAZZでは音がブーミーになってしまった。  
セオリーでは80〜200Hz以下をカットするのが定石だが、「正しいセッティングの基準」がなければ何度やっても同じ失敗を繰り返す。  
そこで、低音から高音まで任意の周波数の正弦波を鳴らせるツールをAndroid向けに自作した。

> 📖 [開発のいきさつを読む（ブログ記事）](https://amekusa03.com/essays/2026-07-09-soundtune)

---

## 主な機能

| 機能 | 説明 |
|------|------|
| 🎵 **正弦波（サイン波）生成** | 任意の周波数（Hz）の音をリアルタイムに生成・再生 |
| 🎶 **5音同時再生** | 独立した5スロットで異なる周波数を同時に鳴らせる |
| 🔊 **出力デバイス自動検知** | 内蔵スピーカー・有線・Bluetoothなどの出力先をリアルタイム表示 |

### 正弦波生成 / 5音同時再生

- `AudioTrack` を使用した PCM 16-bit モノラル、サンプリングレート 44.1kHz でのストリーミング再生
- 5スロット（Tone 1〜5）それぞれに周波数を入力し、独立して Play/Stop できる
- 各スロットは独立した `AudioTrack` とバックグラウンドスレッドで動作

### 出力デバイス検知

- 現在の出力先（内蔵スピーカー / 有線ヘッドホン / Bluetoothスピーカー など）を自動検知
- `AudioManager` を使ってリアルタイム表示

---

## 技術スタック

| 項目 | 内容 |
|------|------|
| プラットフォーム | Android (minSdk: 26 / targetSdk: 37) |
| 言語 | Kotlin |
| ビルドシステム | Gradle (Kotlin DSL) |
| UI | AppCompat + Navigation Component + View Binding + Material Design |
| オーディオ | `AudioTrack` / `AudioManager` |

---

## ファイル構成

```
app/src/main/java/com/kusa/soundtune/
├── AudioEngine.kt    # 音声生成（AudioTrack × 5スロット）の制御ロジック
├── MainActivity.kt   # メインアクティビティ（エッジトゥエッジ設定、ナビゲーションホスト）
└── FirstFragment.kt  # メインUI（5スロット再生・停止制御、デバイス検出）
```

---

## セットアップ

1. **Android Studio**（Koala以降推奨）で本プロジェクトを開く
2. **Gradle Sync**（Sync Project with Gradle Files）を実行
3. Android **8.0（API 26）以上**の実機またはエミュレータにビルド・デプロイして実行
