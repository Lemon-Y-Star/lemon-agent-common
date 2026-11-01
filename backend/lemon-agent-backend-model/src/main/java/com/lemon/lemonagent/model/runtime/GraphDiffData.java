package com.lemon.lemonagent.model.runtime;

import java.util.List;

/**
 * 执行图版本 diff（v4.1：两版图的节点/边增删改，人类可读字符串列表）。
 *
 * @param fromVersion  基线版本
 * @param toVersion    对比版本
 * @param nodesAdded   新增节点：「n3(LLM) 摘要生成」
 * @param nodesRemoved 删除节点
 * @param nodesChanged 变更节点（prompt / tool / tools / approvalPrompt 任一变化）：「n2(LLM) 分诊判断」
 * @param edgesAdded   新增边：「n1 → n3」
 * @param edgesRemoved 删除边
 */
public record GraphDiffData(int fromVersion, int toVersion,
                            List<String> nodesAdded, List<String> nodesRemoved, List<String> nodesChanged,
                            List<String> edgesAdded, List<String> edgesRemoved) {
}
