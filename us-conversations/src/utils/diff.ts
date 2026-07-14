// Text comparison utility for highlighting matches and differences

export interface DiffToken {
  text: string;
  type: 'match' | 'missing' | 'extra';
}

/**
 * Compares spoken text with the target answer word-by-word
 * returns an array of tokens with diff status
 */
export function getWordDiff(target: string, spoken: string): DiffToken[] {
  // Normalize strings: lowercase and strip punctuation (except apostrophes)
  const cleanWord = (w: string) => w.toLowerCase().replace(/[^a-z0-9\s]/g, '').trim();

  const targetWords = target.split(/\s+/).filter(Boolean);
  const spokenWords = spoken.split(/\s+/).filter(Boolean);

  const targetClean = targetWords.map(cleanWord);
  const spokenClean = spokenWords.map(cleanWord);

  const n = targetClean.length;
  const m = spokenClean.length;

  // DP table for Longest Common Subsequence
  const dp: number[][] = Array(n + 1).fill(null).map(() => Array(m + 1).fill(0));

  for (let i = 1; i <= n; i++) {
    for (let j = 1; j <= m; j++) {
      if (targetClean[i - 1] === spokenClean[j - 1]) {
        dp[i][j] = dp[i - 1][j - 1] + 1;
      } else {
        dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
      }
    }
  }

  // Backtrack to find the alignment path
  const diffs: DiffToken[] = [];
  let i = n;
  let j = m;

  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && targetClean[i - 1] === spokenClean[j - 1]) {
      diffs.unshift({
        text: targetWords[i - 1],
        type: 'match'
      });
      i--;
      j--;
    } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
      diffs.unshift({
        text: spokenWords[j - 1],
        type: 'extra'
      });
      j--;
    } else {
      diffs.unshift({
        text: targetWords[i - 1],
        type: 'missing'
      });
      i--;
    }
  }

  return diffs;
}

/**
 * Calculates string similarity score (0 to 100) based on Levenshtein Distance
 */
export function getSimilarityScore(str1: string, str2: string): number {
  const clean = (s: string) => s.toLowerCase().replace(/[^a-z0-9\s]/g, '').replace(/\s+/g, ' ').trim();
  const s1 = clean(str1);
  const s2 = clean(str2);

  if (s1 === s2) return 100;
  if (!s1 || !s2) return 0;

  const track = Array(s2.length + 1).fill(null).map(() => Array(s1.length + 1).fill(undefined));
  for (let i = 0; i <= s1.length; i += 1) track[0][i] = i;
  for (let j = 0; j <= s2.length; j += 1) track[j][0] = j;

  for (let j = 1; j <= s2.length; j += 1) {
    for (let i = 1; i <= s1.length; i += 1) {
      const indicator = s1[i - 1] === s2[j - 1] ? 0 : 1;
      track[j][i] = Math.min(
        track[j][i - 1] + 1, // deletion
        track[j - 1][i] + 1, // insertion
        track[j - 1][i - 1] + indicator // substitution
      );
    }
  }

  const distance = track[s2.length][s1.length];
  const maxLength = Math.max(s1.length, s2.length);
  const similarity = ((maxLength - distance) / maxLength) * 100;
  return Math.round(similarity);
}
