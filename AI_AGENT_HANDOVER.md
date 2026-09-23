# TOEIC Master Corpus & Android Integration: AI Agent 引き継ぎ仕様書

本ドキュメントは、**TOEIC実戦模試高精度マスターコーパス生成パイプライン** および **Androidアプリ（TOEIC Dictation: MyApplication）と Cloud Firestore（	oeic-app-5ac32）の連携仕様** に関する完全な引き継ぎマニュアルです。
後続のAI Agentは本仕様書に基づき、本システムの運用・保守・機能拡張を自律的に遂行してください。

---

## 1. システム全体概要・アーキテクチャ

本システムは、TOEIC模試12回分（全パート・本文＆問題文）から自然言語処理によって抽出された語彙・表現を、アメリカ英語の最高峰辞書（Merriam-Webster）と連携させて構造化し、Android端末での手書き・音声ディクテーション学習を可能にする統合学習プラットフォームです。

`
[入力: 実戦模試12回分]
  ├── 本文データ (Part 3/4 talks, Part 6/7 passage)
  └── 問題文データ (Part 1/2/3/4/5/6/7 questions & choices)
         ↓
【Stage 1: 自然言語処理 (spaCy)】
  ├── 単語 (Words: S/A/B/Cランク判定)
  ├── 句動詞 (Phrasal Verbs: 構文木解析)
  └── コロケーション & イディオム (NPMI・成句分類)
         ↓
【Stage 2: アメリカ英語辞書連携 (WSD & 厳選例文)】
  ├── Merriam-Webster Learner's API (92.1%)
  ├── Longman LDOCE 例文補完 (3.1%)
  └── ローカルキャッシュ (mw_cache/: 2,006語)
         ↓
【Stage 3: データベース投入 (Firestore)】
  └── プロジェクト: toeic-app-5ac32 (11コレクション・全7,040件)
         ↓
【Stage 4: Android アプリ (MyApplication)】
  └── TOEIC Dictation アプリ (手書きOCR / TTS / 音声認識)
`

---

## 2. ディレクトリ構成と主要ファイル

`	ext
c:\Users\kazuy\OneDrive\画像\スクリーンショット\
│
├── AI_AGENT_HANDOVER.md                  # 【本書】AI Agent向け引き継ぎ仕様書
├── TOEIC_CORPUS_SPECIFICATION.md         # パイプライン基本設計書
├── toeic-app-5ac32-firebase-adminsdk-... # Firestore接続用サービスアカウントキー
├── upgrade_longman_to_mw.py              # Longman補完語のMW一括アップグレードスクリプト
│
├── corpus_extracted_texts/               # 【中間テキスト】パート別本文・問題文JSON (11種)
│   ├── part1_questions.json
│   ├── part3_talks.json
│   └── ...
│
├── corpus_results/                       # 【最終生成成果物】CSV (UTF-8 BOM) & JSON
│   ├── toeic_master_vocab_all_parts.csv  # 全7,040件 統合マスターCSV
│   ├── toeic_master_vocab_all_parts.json # 全7,040件 統合マスターJSON
│   ├── vocab_part1_questions.csv / .json # Part 1 問題文 (165件)
│   ├── vocab_part2_questions.csv / .json # Part 2 問題文 (449件)
│   ├── vocab_part3_talks.csv / .json     # Part 3 会話本文 (753件)
│   ├── vocab_part3_questions.csv / .json # Part 3 問題文 (691件)
│   ├── vocab_part4_talks.csv / .json     # Part 4 説明本文 (716件)
│   ├── vocab_part4_questions.csv / .json # Part 4 問題文 (615件)
│   ├── vocab_part5_questions.csv / .json # Part 5 問題文 (585件)
│   ├── vocab_part6_passage.csv / .json   # Part 6 長文本文 (311件)
│   ├── vocab_part6_choices.csv / .json   # Part 6 選択肢 (200件)
│   ├── vocab_part7_passage.csv / .json   # Part 7 長文本文 (1,647件)
│   └── vocab_part7_questions.csv / .json # Part 7 問題文 (908件)
│
├── mw_cache/                             # 【辞書キャッシュ】MW公式生データ (2,006語分)
├── phrasal_verbs/                        # 句動詞分析資産
└── collocations_idioms/                  # コロケーション・イディオム分析資産
`

---

## 3. Firestore（	oeic-app-5ac32）スキーマ仕様

Androidアプリのデータモデル Sentence（id, example, page）および進捗管理と完全互換を保つため、以下のスキーマで11コレクション（計7,040件）が作成されています。

### コレクション一覧 (collectionId)
1. 	oeic_exam_part_1_questions (165件, page 1〜165)
2. 	oeic_exam_part_2_questions (449件, page 1〜449)
3. 	oeic_exam_part_3_talks (753件, page 1〜753)
4. 	oeic_exam_part_3_questions (691件, page 1〜691)
5. 	oeic_exam_part_4_talks (716件, page 1〜716)
6. 	oeic_exam_part_4_questions (615件, page 1〜615)
7. 	oeic_exam_part_5_questions (585件, page 1〜585)
8. 	oeic_exam_part_6_passage (311件, page 1〜311)
9. 	oeic_exam_part_6_choices (200件, page 1〜200)
10. 	oeic_exam_part_7_passage (1,647件, page 1〜1,647)
11. 	oeic_exam_part_7_questions (908件, page 1〜908)

### ドキュメント・プロパティ仕様

| プロパティ名 | 型 | 説明・用途 |
| :--- | :--- | :--- |
| ook_title | string | コレクション名（例: 	oeic_exam_part_3_talks） |
| page | number (int) | **1から始まる連番**（アプリのページネーション・学習進捗用） |
| example | string | **ディクテーション練習用例文**（MW厳選例文、または模試出現文） |
| expression | string | 見出し語・語句（例: complete, set up, 	hank you for） |
| category | string | 言語区分: word / phrasal_verb / collocation_idiom |
| category_jp | string | 表示用区分: 単語 / 句動詞 / イディオム・コロケーション |
| part | string | 英語適語パートID（例: part_3_talks, part_1_questions） |
| part_label | string | 表示用ラベル（例: Part 3 (Talks)） |
| source_type | string | 本文/問題文種別: 	alks / passage / questions / choices |
| importance_rank | string | 出現頻度ランク: S (最重要) / A (超頻出) / B (頻出) / C (発展) |
| pos | string | 品詞（NOUN, VERB, ADJ, ADV, VERB_PHRASE 等） |
| ipa | string | アメリカ英語 発音記号 (IPA) |
| meaning_japanese| string | 日本語訳（句動詞・イディオム等） |
| selected_definition| string | **文脈合致英英定義** (WSD採択済) |
| est_dictionary_example| string | Merriam-Webster 厳選アメリカ英語例文 |
| ctual_toeic_sentence | string | TOEIC模試実際の出題センテンス（太字ハイライト付き） |
| dictionary_source | string | 辞書出典: Merriam-Webster (92.1%) / Longman (3.1%) |
| 	est_count | number (int) | 模試出現テスト数 (1〜12) |
| 	otal_count | number (int) | 模試内総出現回数 |
| ppeared_tests | string | 出現模試番号一覧 (	est1, test3, test5...) |

---

## 4. Android アプリ（MyApplication）連携仕様

* **アプリパス**: C:\Users\kazuy\AndroidStudioProjects\MyApplication
* **対象画面**: pp/src/main/java/com/example/myapplication/ui/home/HomeScreen.kt

### ホーム画面へのカテゴリ追加手順
後続のAI Agentは、HomeScreen.kt の categories リストに以下のエントリを追加することで、アプリのホーム画面に11個のパート別学習カードを表示させることができます：

`kotlin
// HomeScreen.kt 内の categories リストに追加
val categories = listOf(
    // === TOEIC 実戦マスター (全パート・本文/問題文別) ===
    toeic_exam_part_1_questions to Exam Part 1 (Questions),
    toeic_exam_part_2_questions to Exam Part 2 (Questions),
    toeic_exam_part_3_talks to Exam Part 3 (Conversations),
    toeic_exam_part_3_questions to Exam Part 3 (Questions),
    toeic_exam_part_4_talks to Exam Part 4 (Talks),
    toeic_exam_part_4_questions to Exam Part 4 (Questions),
    toeic_exam_part_5_questions to Exam Part 5 (Incomplete Sentences),
    toeic_exam_part_6_passage to Exam Part 6 (Passages),
    toeic_exam_part_6_choices to Exam Part 6 (Choices),
    toeic_exam_part_7_passage to Exam Part 7 (Reading Passages),
    toeic_exam_part_7_questions to Exam Part 7 (Questions),

    // === 既存教材 ===
    nakamura_vocabulary_1_part_1 to Nakamura Vocab 1 (Part 1),
    ...
)
`

---

## 5. 運用・保守・再生成コマンド集

### (1) Longman補完語をMWへアップグレードする場合
`powershell
python upgrade_longman_to_mw.py
`
* dictionary_source == Longman の語彙を抽出し、MW APIから自動再取得して全ファイルを更新します。

### (2) Firestoreへの再アップロード手順
`powershell
python -c 
import os
# scratch/upload_to_firestore.py を実行
os.system('python C:/Users/kazuy/.gemini/antigravity/brain/ab7195eb-02aa-4842-a67d-a2bf8441f7f3/scratch/upload_to_firestore.py')

`

---

## 6. 後続 AI Agent への留意事項・注意事項

1. **Merriam-Webster API レートリミット**:
   * 無料APIキー（9c0898f5-93c0-4bad-b987-dd3e17063cdc）は **1日 1,000 リクエスト上限** です。
   * リセットは **UTC 00:00（日本時間 午前9:00）**。
   * 既に mw_cache/ に **2,006 語の生データがキャッシュ** されているため、キャッシュ優先ロジックを崩さないこと。
2. **文字コード**:
   * 出力CSVは必ず **utf-8-sig (UTF-8 with BOM)** で出力し、Windows Excelでの文字化けを防ぐこと。
3. **Androidアプリの example フィールド**:
   * アプリのTTS読み上げおよび手書き認識正解判定は example フィールドのテキストと完全一致で照合されます。余計なマークダウン（** や [test1] 等）を含めないこと。

---

## 7. TTS (音声読み上げ) アーキテクチャ仕様（採用決定）

> 詳細は **[TTS_SPECIFICATION.md](file:///c:/Users/kazuy/AndroidStudioProjects/MyApplication/TTS_SPECIFICATION.md)** を参照。

TOEIC ディクテーション学習アプリのTTS再生アルゴリズムとして、以下のアーキテクチャが公式採用されています：

1. **ページ表示時バックグラウンド先読み（プリロード）**:
   - `PracticeUiState.Success` 受信時およびページ遷移時に `ttsManager.preloadText(sentence.example)` を自動トリガー。
   - ユーザーが英文を読んでいる裏で非同期フェッチ完了（所要0.3〜0.5秒）。
2. **単一ファイル上書きキャッシュによる容量肥大化防止**:
   - キャッシュは `context.cacheDir/current_page_tts.mp3` の1ファイルのみ（数十KB）。
   - ページを何百回めくっても端末ストレージが蓄積・肥大化しない安全設計。
3. **ゼロ遅延即時再生**:
   - 再生ボタン（▶）またはスマートペンの A/AUDIO ボタン押下時、ローカルファイルから `MediaPlayer` で即時再生（約 5〜10ms）。
   - チャンクストリーミング再生（AudioTrack）はバッファアンダーランによる音割れ・雑音が発生するため完全廃止済み。

※ Image to text (手書き文字起こし) は現在検証中のため、確定次第追記予定。

---

## 8. 実践口語コーパス（Justin Waller Street Interviews）対応仕様

YouTube 街頭インタビューに基づく実践的な口語コーパス（`justin_waller_vocabulary`）の連携仕様です。

1. **コレクション名 / 教材ID**:
   - `justin_waller_vocabulary`（`HomeScreen.kt` および `ScoreScreen.kt` で登録済み）
2. **データモデル (`Sentence.kt`)**:
   - `selectedDefinition`: 英英辞書の定義文
   - `spokenCorpusExample`: YouTube 街頭インタビューの生セリフ引用情報（`speakerLine`, `videoTitle`, `videoUrl`, `sourceChannel`）
   - `cefrLevel`: CEFR 英語運用能力レベル（例: `"B1"`）
   - `formality`: フォーマリティ（例: `"casual-neutral"`）
   - `cocaRank`: COCA コーパス頻度順位
   - `channelOccurrences`: チャンネル内での出現頻度
   - `ipa`: 発音記号
3. **UI 表示および学習ロジック (`PracticeScreen.kt`, `PracticeViewModel.kt`)**:
   - **メイン本文 (`displayExample`)**:
     - `bestDictionaryExample`（Merriam-Webster 等の辞書標準例文）が存在する場合はそれを本文に優先採用。
     - ディクテーション練習・手書き判定・タイピング採点・TTS 読み上げ・Gemini 和訳もすべてこの標準例文を対象に実行。
   - **街頭インタビュー動画カード (`spokenCorpusExample`)**:
     - ネイティブのリアルな生セリフ（`speakerLine`）を配置。
     - **右上にコンパクト配置されたアクションアイコン**:
       - **翻訳アイコン (`Translate`)**: 街頭インタビューの生セリフ（`speakerLine`）を Gemini 3.5 Flash Lite でワンタップ和訳し、カード内にアコーディオン表示。
       - **YouTube 視聴アイコン (`OpenInNew`)**: 誤タップ防止のため右上に小さく配置。タップ時に公式 YouTube アプリ（または Chrome ブラウザ）への正規 Intent 遷移（`Intent.ACTION_VIEW`）を行い、YouTube 利用規約（ToS）に 100% 準拠。
     - 「標準英語でのディクテーション練習」と「動画でのリアルな生口語の体感・和訳確認」を重複なく両立。
   - 英英定義文（DEFINITION）および `CEFR B1`, `Casual-Neutral`, `309x channel • COCA #397` バッジをカード内に表示。
   - **アクションバーの整理**: 英文カード右上に翻訳ボタンを独立配置したため、ボトムバー（TTS 再生ボタンの横）の重複していた翻訳ボタンは完全削除。


