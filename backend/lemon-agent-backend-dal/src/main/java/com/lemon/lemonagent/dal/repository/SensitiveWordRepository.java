package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.SensitiveWordEntity;

import java.util.List;

/** 敏感词仓储端口。 */
public interface SensitiveWordRepository {

    /**
     * 全部敏感词（按词排序）。
     *
     * @return 出参：词列表
     */
    List<SensitiveWordEntity> findAll();

    /**
     * 按词查（防重复添加）。
     *
     * @param word 入参：敏感词
     * @return 出参：实体；不存在则 null
     */
    SensitiveWordEntity findByWord(String word);

    /**
     * 插入。
     *
     * @param e 入参：实体
     * @return 出参：影响行数
     */
    int insert(SensitiveWordEntity e);

    /**
     * 删除。
     *
     * @param id 入参：主键
     * @return 出参：影响行数
     */
    int delete(Long id);

}
