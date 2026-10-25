package com.lemon.lemonagent.servicecore.safety;

import com.lemon.lemonagent.dal.entity.SensitiveWordEntity;
import com.lemon.lemonagent.dal.repository.SensitiveWordRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词过滤（v3.9.2，内容安全最小充分版）：平台级词库，输入拦截 + 输出脱敏共用。
 *
 * <p>处理策略：输入**拒绝**（发都发不进去）；输出**脱敏**（命中替换为【已屏蔽】，不拦整条回答——
 * 拦整条会让用户什么上下文都看不到，脱敏既合规又保留信息量）。
 * 词表缓存于内存，增删后立即刷新；词库为空时零开销直通。</p>
 */
@Service
public class SensitiveWordService {

    /** 输出脱敏的替换标记（自解释，避免与 markdown 语法混淆）。 */
    public static final String MASK = "【已屏蔽】";

    private final SensitiveWordRepository repository;

    /** 词表缓存：懒加载，增删后刷新（volatile 保证跨线程可见）。 */
    private volatile List<String> cached;

    public SensitiveWordService(SensitiveWordRepository repository) {
        this.repository = repository;
    }

    /** 词表（缓存）。 */
    public List<String> words() {
        List<String> c = cached;
        if (c == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = repository.findAll().stream()
                            .map(SensitiveWordEntity::getWord).toList();
                }
                c = cached;
            }
        }
        return c;
    }

    /** 词库是否启用（有词才过滤，空库零开销直通）。 */
    public boolean enabled() {
        return !words().isEmpty();
    }

    /** 全部词（管理端列表）。 */
    public List<SensitiveWordEntity> list() {
        return repository.findAll();
    }

    /** 添加词（去重）。 */
    public SensitiveWordEntity add(String word) {
        String w = word == null ? "" : word.trim();
        if (w.isEmpty()) {
            throw new IllegalArgumentException("敏感词不能为空");
        }
        SensitiveWordEntity existing = repository.findByWord(w);
        if (existing != null) {
            return existing;
        }
        SensitiveWordEntity e = new SensitiveWordEntity();
        e.setWord(w);
        repository.insert(e);
        refresh();
        return e;
    }

    /** 删除词。 */
    public void remove(Long id) {
        repository.delete(id);
        refresh();
    }

    private void refresh() {
        cached = repository.findAll().stream()
                .map(SensitiveWordEntity::getWord).toList();
    }

    /** 命中的词（输入拦截用，提示用户命中了什么）。 */
    public List<String> hits(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        for (String w : words()) {
            if (text.contains(w) && !result.contains(w)) {
                result.add(w);
            }
        }
        return result;
    }

    /** 脱敏：全部命中词替换为【已屏蔽】。 */
    public String mask(String text) {
        String result = text;
        for (String w : words()) {
            result = result.replace(w, MASK);
        }
        return result;
    }

    /**
     * 流式切分：敏感词可能被切在两个 token 之间——返回 [可安全发出的部分（已脱敏）, 需缓冲的尾部]。
     *
     * <p>{@code keep} 为全库最长词长度 - 1：尾部保留这么多字符不立即下发，与下一片拼接后再判定，
     * 保证跨 token 的敏感词也能命中。词库为空时整体直通。</p>
     *
     * @param pending 入参：累积的未发射文本
     * @param keep    入参：尾部保留字符数（调用方按最长词计算）
     * @return 出参：[已脱敏的可发射文本, 缓冲尾部]
     */
    public String[] maskStreaming(String pending, int keep) {
        if (words().isEmpty()) {
            return new String[]{pending, ""};
        }
        int safeLen = pending.length();
        for (String w : words()) {
            int maxL = Math.min(w.length() - 1, pending.length());
            for (int l = maxL; l >= 1; l--) {
                if (pending.regionMatches(pending.length() - l, w, 0, l)) {
                    safeLen = Math.min(safeLen, pending.length() - l);
                    break;
                }
            }
        }
        String emit = pending.substring(0, safeLen);
        String rest = pending.substring(safeLen);
        return new String[]{mask(emit), rest};
    }

}
