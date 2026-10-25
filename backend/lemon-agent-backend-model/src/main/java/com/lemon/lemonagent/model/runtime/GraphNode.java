package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 执行图节点（PRD FR-7.1 §② 最小模型，v3.2.0）。
 *
 * <p>字段克制：不做表达式引擎、不做 onError 分支、不做子图。节点类型的语义见 {@link GraphNodeType}。</p>
 */
@Data
public class GraphNode {

    /** 节点 ID（图内唯一，如 n1） */
    private String id;

    /** 节点类型：START / LLM / TOOL / APPROVAL / END */
    private String type;

    /** 展示名（可空，前端画布与运行卡片用） */
    private String title;

    /** LLM 节点指令（可作为该节点的局部系统提示；空则只用人格 + 上游产出） */
    private String prompt;

    /** TOOL 节点：工具名（必须已注册） */
    private String tool;

    /** TOOL 节点：固定入参（作者显式配置 = 已授权，不再走审批） */
    private Map<String, Object> args;

    /** APPROVAL 节点：审批提示语 */
    private String approvalPrompt;

    /** 节点级工具白名单（PRD FR-6.5 三态）：null = 继承 Agent；[] = 不装配；数组 = 精确授权 */
    private List<String> tools;

    /** 画布坐标 X（仅前端排版用，调度不读；null = 由前端自动布局） */
    private Integer x;

    /** 画布坐标 Y */
    private Integer y;

}
