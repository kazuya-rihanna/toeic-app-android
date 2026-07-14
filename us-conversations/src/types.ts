/* ============================================================
   Shared Types, Constants & Enums — US Conversations App
============================================================ */

export interface Conversation {
  id: number;
  cat: string;
  q: string;   // question / partner statement
  q_ja?: string; // Japanese translation of partner statement
  qa: string;  // your English answer
  qn: string;  // Japanese nuance / translation
}

export type TabType = 'dashboard' | 'speech' | 'practice' | 'explorer' | 'settings';
export type PracticeMode = 'speech' | 'typing' | 'flashcard';
export type MasteryStatus = 'unattempted' | 'review' | 'mastered';

/* ---- Categories ---- */
export const CATEGORIES = [
  { id: 'all',               label: 'All Conversations',    jp: 'すべての会話' },
  { id: 'Greetings',         label: 'Greetings',            jp: '出会いと最初の挨拶' },
  { id: 'Self-Introduction', label: 'Self-Introduction',    jp: '自己紹介と趣味・バスケ' },
  { id: 'Work & Career',     label: 'Work & Career',        jp: '現在の仕事とこれまでのキャリア' },
  { id: 'Tech & Apps',       label: 'Tech & Apps',          jp: 'アプリ開発と英語学習' },
  { id: 'Career Vision',     label: 'Career Vision',        jp: '将来の転職と独立ビジョン' },
  { id: 'Saitama Guide',     label: 'Saitama Guide',        jp: '埼玉の案内とおもてなし' },
] as const;

/* ---- Full Self-Introduction Speech ---- */
export const FULL_SPEECH = {
  english: `I'm 38 years old. I love listening to music, especially Eminem and Rihanna. In my free time, I play basketball regularly to stay active and healthy. I actually went to college and majored in computer science, but I had to drop out due to financial reasons. After working for an auto parts manufacturer, I changed jobs. Now, I work part-time in customer service for a restaurant business, handling complaints. This part-time schedule allows me to dedicate time to developing web and native mobile apps. In fact, I developed my own English learning app and I'm using it to study English right now! Eventually, I want to transition to a sales representative role at a semiconductor trading company, or apply for jobs at IT companies. I'm also open to starting my own business down the road.`,
  japanese: `38歳です。音楽を聴くのが好きで、特にエミネムとリアーナが大好きです。普段は健康維持と運動のためにバスケットボールをプレイしています。大学では情報工学を専攻していましたが、金銭的な理由で中退しました。自動車部品メーカーで働いた後、転職して今は飲食関連の会社でパートタイムとしてクレーム処理の仕事をしています。このパートタイムのスケジュールのおかげで、ウェブやモバイルアプリの開発に時間を充てることができています。実は、英語学習用のアプリを自分で開発し、今まさにそれを使って英語を勉強しています！いずれは、半導体商社の営業職に転職するか、IT企業に応募したいと考えています。将来的には独立することも視野に入れています。`,
};
