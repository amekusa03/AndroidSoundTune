# SoundTune

SoundTuneは、任意の周波数の正弦波音源をリアルタイムで生成し、イコライザー調整や出力デバイスの検知を行うことができるAndroid用オーディオツールアプリケーションです。

## 主な機能

- **正弦波（サイン波）の生成**
  - 入力された任意の周波数（Hz）の音をリアルタイムに生成して再生します。
  - `AudioTrack` を使用した PCM 16-bit モノラル、サンプリングレート 44.1kHz でのストリーミング再生。
- **イコライザー（Equalizer）調整**
  - Android標準の `Equalizer` APIを利用し、周波数帯域ごとのゲイン（レベル）をスライダー（SeekBar）で動的に調整できます。
  - デバイスがサポートしているバンド数や帯域幅を自動的に取得し、UIを動的に構築します。
- **出力デバイスの自動検知**
  - 現在のオーディオ出力先（内蔵スピーカー、有線ヘッドホン/ヘッドセット、Bluetoothスピーカー/ヘッドセットなど）を自動検知して画面にリアルタイム表示します。

## 技術スタック / 仕様

- **プラットフォーム**: Android (minSdk: 26, targetSdk: 37)
- **言語**: Kotlin
- **ビルドシステム**: Gradle (Kotlin DSL)
- **アーキテクチャ・UIコンポーネント**:
  - Jetpack (AppCompat, Navigation Component)
  - View Binding (XMLベースのレイアウトデータバインディング)
  - Material Design Components
- **オーディオAPI**:
  - `AudioTrack` (音声ストリーミング)
  - `Equalizer` (`android.media.audiofx.Equalizer`)
  - `AudioManager` (出力デバイス情報の取得)

## 主要ファイル構成

```text
app/src/main/java/com/kusa/soundtune/
├── AudioEngine.kt    # 音声生成（AudioTrack）およびイコライザーの制御ロジック
├── MainActivity.kt   # メインアクティビティ（エッジトゥエッジ設定、ナビゲーションホスト）
└── FirstFragment.kt  # メインUI（再生・停止制御、イコライザーUI動的生成、デバイス検出）
```

## セットアップと実行方法

1. **Android Studio** (Koala 以降推奨) を起動します。
2. 本プロジェクトをインポートします。
3. Gradleの同期（Sync Project with Gradle Files）を実行します。
4. Android 8.0 (API 26) 以上の実機またはエミュレータにビルド・デプロイして実行します。
