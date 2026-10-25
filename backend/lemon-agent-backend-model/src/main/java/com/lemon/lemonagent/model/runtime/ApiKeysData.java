package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** API 密钥列表。 */
@Data
public class ApiKeysData {

    /** 密钥 */
    private List<ApiKeyData> keys;

}
