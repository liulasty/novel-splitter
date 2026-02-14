
/**
 * 简单的 Token 用量估算
 * 
 * 基于常见大模型（如 DeepSeek, Qwen, Llama）的字符/Token 换算比例：
 * - 1 个中文字符 ≈ 0.6 个 token
 * - 1 个英文字符 ≈ 0.3 个 token
 * 
 * 注意：这是离线估算，仅供参考，实际计费以模型 API 返回的 usage 为准。
 */
export const estimateTokens = (text: string): number => {
  if (!text) return 0;

  // 匹配中文字符 (基本汉字范围)
  const chineseRegex = /[\u4e00-\u9fa5]/g;
  const chineseMatches = text.match(chineseRegex);
  const chineseCount = chineseMatches ? chineseMatches.length : 0;
  
  // 其他字符 (包括英文、数字、标点、换行等)
  const otherCount = text.length - chineseCount;
  
  // 计算 Token
  // 中文: 0.6 * count
  // 其他: 0.3 * count
  const tokens = (chineseCount * 0.6) + (otherCount * 0.3);
  
  // 向上取整
  return Math.ceil(tokens);
};
