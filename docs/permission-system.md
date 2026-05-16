# BetterResidence 权限系统说明

BetterResidence 的领地权限使用“权限组权重”来判断玩家能不能做某件事。

你可以把它理解成一条从低到高的权限刻度：

```text
-1000        -100          0             900          1000
任何人 ---- 黑名单 ---- 访客 ---- 成员/自定义组 ---- 所有者
```

每个玩家在每个领地里都有一个“当前权限组”，每个权限也有一个“最低需要的权限组”。

判断规则很简单：

```text
玩家权限组权重 >= 权限要求权重  =>  允许
玩家权限组权重 <  权限要求权重  =>  拒绝
```

例如，某个玩家是 `成员`，权重是 `900`。如果 `破坏方块` 要求 `访客`，也就是权重 `0`，那么：

```text
900 >= 0
```

所以成员可以破坏方块。

如果某个玩家是 `黑名单`，权重是 `-100`，而 `破坏方块` 要求 `访客`，那么：

```text
-100 >= 0
```

不成立，所以黑名单玩家不能破坏方块。

## 默认权限组

系统中有几个常见的权限组概念。

| 名称 | 权重 | 是否是真实权限组 | 说明 |
| --- | ---: | --- | --- |
| 任何人 | -1000 | 否 | 只用于设置权限，表示包括黑名单在内的所有人 |
| 黑名单 | 通常为负数 | 是 | 用来限制玩家，比访客权限还低 |
| 访客 | 0 | 否 | 玩家没有加入任何权限组时的状态 |
| 成员 | 通常为 900 | 是 | 默认信任组 |
| 所有者 | 1000 | 是 | 领地所有者，拥有最高权限 |

`访客` 不是一个实际保存的权限组。玩家没有被加入任何权限组时，就会被视为访客。

`任何人` 也不是一个实际权限组。它只能在设置权限时使用，用来表示最低要求为 `-1000`。

## 黑名单怎么工作

负数权限组最适合用来做黑名单。

假设有一个黑名单组：

```text
黑名单 = -100
访客 = 0
成员 = 900
所有者 = 1000
```

如果你把一个权限开放给 `访客`：

```text
/res set block.break 访客
```

那么访客、成员、所有者都可以使用这个权限，但黑名单不可以。

原因是：

```text
访客:    0 >= 0      允许
成员:  900 >= 0      允许
黑名单: -100 >= 0    拒绝
```

如果你确实希望连黑名单也能做某件事，可以把权限设置给 `任何人`：

```text
/res set block.break 任何人
```

这会把权限要求设置为 `-1000`，所有合法权限组都会满足这个要求。

## 常用命令

### 给玩家加入权限组

```text
/res trust <玩家> [权限组]
```

如果不写权限组，默认加入 `成员`。

例如：

```text
/res trust Steve
/res trust Steve 成员
/res trust Steve 黑名单
```

### 移除玩家权限组

```text
/res untrust <玩家>
```

移除后，玩家会回到 `访客` 状态。

也可以使用：

```text
/res trust <玩家> 访客
```

### 设置某个权限的最低权限组

```text
/res set <权限> <权限组>
```

例如：

```text
/res set block.break 成员
/res set block.place 访客
/res set block.interact 任何人
/res set admin 所有者
```

含义分别是：

| 命令 | 效果 |
| --- | --- |
| `/res set block.break 成员` | 成员及以上可以破坏方块 |
| `/res set block.place 访客` | 访客及以上可以放置方块，但黑名单不可以 |
| `/res set block.interact 任何人` | 所有人都可以交互方块，包括黑名单 |
| `/res set admin 所有者` | 只有所有者可以管理领地权限 |

## 权限名称

当前常见权限包括：

| 权限 | 说明 |
| --- | --- |
| `block.break` | 破坏方块 |
| `block.place` | 放置方块 |
| `block.interact` | 与方块交互 |
| `entity.damage` | 攻击实体 |
| `entity.interact` | 与实体交互 |
| `admin` | 管理领地权限 |

权限支持通配符。

例如：

```text
block.*
```

表示所有 `block` 开头的权限，例如破坏、放置、交互方块。

```text
*
```

表示所有权限。

## 针对具体方块或实体设置权限

部分权限可以带目标后缀，格式是：

```text
权限:目标
```

例如：

```text
/res set block.break:oak_log 成员
/res set block.interact:container 访客
/res set entity.damage:zombie 成员
```

这些命令的含义是：

| 命令 | 效果 |
| --- | --- |
| `/res set block.break:oak_log 成员` | 成员及以上可以破坏橡木原木 |
| `/res set block.interact:container 访客` | 访客及以上可以交互容器类方块 |
| `/res set entity.damage:zombie 成员` | 成员及以上可以攻击僵尸 |

目标可以是具体材料或实体，也可以是预设目标组。

常见目标组：

| 目标组 | 说明 |
| --- | --- |
| `all` | 所有目标 |
| `container` | 容器类方块，例如箱子等 |

## 多条规则同时匹配时怎么选

如果多条权限规则都能匹配同一个行为，系统会选择更具体的规则。

一般可以这样理解：

```text
具体权限 > 分类通配符 > 全局通配符
具体目标 > 目标组 > 无目标
```

例如同时存在：

```text
/res set block.* 访客
/res set block.break 成员
```

玩家破坏方块时，会优先使用 `block.break`，因为它比 `block.*` 更具体。

再例如同时存在：

```text
/res set block.break:all 访客
/res set block.break:container 成员
```

玩家交互容器类方块时，会优先使用 `container` 这条更具体的目标规则。

## 推荐用法

### 默认安全配置

如果你希望新领地默认只有所有者能做事，可以把默认权限都设置为 `所有者` 权重，也就是 `1000`。

然后让领地主按需开放：

```text
/res set block.break 成员
/res set block.place 成员
/res set block.interact 访客
```

### 使用黑名单

推荐把黑名单组设置成负数，例如 `-100`。

这样当权限开放给 `访客` 时，黑名单仍然会被排除。

```text
/res trust Steve 黑名单
/res set block.interact 访客
```

此时普通访客可以交互方块，但 Steve 不能。

### 开放给所有人

如果某个行为真的不需要限制，包括黑名单也可以做，就设置为 `任何人`：

```text
/res set block.interact 任何人
```

谨慎使用 `任何人`，因为它会绕过黑名单组的限制。

## 配置权限组名称

权限组可以在配置文件中设置名称和权重。

名称支持两种写法：

```yaml
name: "lang:claim.group.trusted"
```

这种写法会从语言文件中读取显示名，方便翻译。

也可以直接写固定名称：

```yaml
name: "成员"
```

权重必须在 `-1000` 到 `1000` 之间。

`owner` 和 `trusted` 是必须配置的权限组，其中 `owner` 的权重必须是 `1000`。

示例：

```yaml
claim:
  groups:
    owner:
      name: "lang:claim.group.owner"
      weight: 1000
    trusted:
      name: "lang:claim.group.trusted"
      weight: 900
    blacklisted:
      name: "lang:claim.group.blacklisted"
      weight: -100
```

## 一句话总结

权限组权重越高，权限越大；权限要求越低，开放范围越广。

`访客` 是 `0`，`黑名单` 应该低于 `0`，`任何人` 是最低的 `-1000`，`所有者` 是最高的 `1000`。
