package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.ToolsData;
import com.lemon.lemonagent.servicecore.runtime.ToolRegistry;
import com.lemon.architecture.model.BaseResult;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 可授权工具目录（v3.0 Agent 三要素：工具授权）。
 *
 * <p>只读接口：返回注册表里的工具及「是否需审批 / 当前是否可用」，
 * 前端「我的 Agent」表单据此渲染勾选框。v3.9 起 run_wf_*（发布为工具的工作流）
 * 只对发布者可见。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/tools")
public class ToolCatalogController {

    /** 工具注册表。 */
    @Resource
    private ToolRegistry toolRegistry;

    /**
     * 工具目录（按当前用户过滤）。
     *
     * @param userId 当前用户
     * @return 出参：工具清单
     */
    @GetMapping
    public BaseResult<ToolsData> list(@RequestAttribute("lemon.userId") Long userId) {
        ToolsData data = new ToolsData();
        data.setTools(toolRegistry.catalog(userId));
        return BaseResult.success(data);
    }

}
