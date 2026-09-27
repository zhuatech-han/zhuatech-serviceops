// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 数据模型及版本化业务记录。知华科技 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
public final class Models {
  private Models() {}

  /** 主键、乐观锁与时间信息。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @MappedSuperclass
  public abstract static class Base {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version public long version;

    @Column(nullable = false)
    public LocalDateTime createdAt;

    @Column(nullable = false)
    public LocalDateTime updatedAt;

    @PrePersist
    void created() {
      createdAt = updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    @PreUpdate
    void updated() {
      updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
  }

  /** 部门数据范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @MappedSuperclass
  public abstract static class Scoped extends Base {
    @Column(nullable = false)
    public Long orgId;
  }

  /** organizations 持久化记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Organization")
  @Table(name = "organizations")
  public static class Organization extends Base {
    @Column(length = 200)
    public String name = "";

    public boolean enabled;
  }

  /** roles 持久化记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Role")
  @Table(name = "roles")
  public static class Role extends Base {
    @Column(length = 120)
    public String name = "";

    @Column(length = 2000)
    public String permissions = "";

    public boolean builtin;
  }

  /** accounts 持久化记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Account")
  @Table(name = "accounts")
  public static class Account extends Base {
    @Column(length = 120)
    public String username = "";

    @Column(length = 200)
    public String displayName = "";

    @JsonIgnore
    @Column(length = 200)
    public String passwordHash = "";

    public Long orgId;
    public Long roleId;
    public Long customerId;
    public boolean enabled;
  }

  /** customers 持久化记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Customer")
  @Table(name = "customers")
  public static class Customer extends Scoped {
    @Column(length = 200)
    public String name = "";

    @Column(length = 500)
    public String site = "";

    @Column(length = 200)
    public String contact = "";

    public boolean enabled;
  }

  /** assets 持久化记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Asset")
  @Table(name = "assets")
  public static class Asset extends Scoped {
    @Column(length = 200)
    public String name = "";

    public Long customerId;

    @Column(length = 200)
    public String serialNumber = "";

    @Column(length = 500)
    public String location = "";

    public LocalDate warrantyUntil;
    public boolean enabled;
  }

  /** contracts 持久化记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Contract")
  @Table(name = "contracts")
  public static class Contract extends Scoped {
    @Column(length = 200)
    public String name = "";

    public Long customerId;
    public Long assetId;
    public LocalDate startDate;
    public LocalDate endDate;
    public int responseHours;
    public int resolutionHours;
    public boolean enabled;
  }

  /** work_orders 持久化记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "WorkOrder")
  @Table(name = "work_orders")
  public static class WorkOrder extends Scoped {
    @Column(length = 200)
    public String name = "";

    public Long customerId;
    public Long assetId;
    public Long contractId;
    public Long assigneeId;

    @Column(length = 200)
    public String state = "";

    @Column(length = 200)
    public String priority = "";

    @Column(length = 2000)
    public String description = "";

    @Column(length = 2000)
    public String diagnosis = "";

    @Column(length = 2000)
    public String resolution = "";

    @Column(length = 2000)
    public String reason = "";

    @Column(length = 200)
    public String coverage = "";

    public LocalDateTime dueAt;
    public LocalDateTime respondBy;
    public LocalDateTime acceptedAt;
    public LocalDateTime resolvedAt;
    public LocalDateTime closedAt;
    public int laborMinutes;

    @Column(precision = 14, scale = 2)
    public BigDecimal laborRate = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    public BigDecimal partCost = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    public BigDecimal quotedAmount = BigDecimal.ZERO;

    @Column(length = 200)
    public String settlementState = "";

    @Column(length = 200)
    public String paymentReference = "";

    public boolean safetyChecked;
    public boolean testPassed;
    public LocalDateTime waitingAt;
    public boolean quoteApproved;
    public Long planId;
    public LocalDate plannedDate;
  }

  /** parts 持久化记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Part")
  @Table(name = "parts")
  public static class Part extends Scoped {
    @Column(length = 200)
    public String name = "";

    @Column(length = 120)
    public String sku = "";

    @Column(length = 200)
    public String unit = "";

    public int stock;

    @Column(precision = 14, scale = 2)
    public BigDecimal unitCost = BigDecimal.ZERO;

    public boolean enabled;
  }

  /** movements 持久化记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Movement")
  @Table(name = "movements")
  public static class Movement extends Scoped {
    public Long partId;
    public Long orderId;

    @Column(length = 200)
    public String kind = "";

    public int quantity;

    @Column(precision = 14, scale = 2)
    public BigDecimal unitCost = BigDecimal.ZERO;

    @Column(length = 120)
    public String requestKey = "";

    public Long issueId;
    public Long actorId;

    @Column(length = 2000)
    public String reason = "";
  }

  /** plans 持久化记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Plan")
  @Table(name = "plans")
  public static class Plan extends Scoped {
    @Column(length = 200)
    public String name = "";

    public Long assetId;
    public LocalDate nextDate;
    public int intervalDays;
    public boolean enabled;

    @Column(length = 2000)
    public String description = "";
  }

  /** knowledge 持久化记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Knowledge")
  @Table(name = "knowledge")
  public static class Knowledge extends Scoped {
    @Column(length = 200)
    public String name = "";

    @Column(length = 200)
    public String category = "";

    @Column(length = 2000)
    public String content = "";

    public boolean published;
  }

  /** audit_events 持久化记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Audit")
  @Table(name = "audit_events")
  public static class Audit extends Scoped {
    @Column(length = 200)
    public String actor = "";

    @Column(length = 200)
    public String action = "";

    @Column(length = 200)
    public String resource = "";

    @Column(length = 2000)
    public String detail = "";
  }

  /** config_entries 持久化记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "ConfigEntry")
  @Table(name = "config_entries")
  public static class ConfigEntry extends Scoped {
    @Column(length = 200)
    public String name = "";

    @Column(length = 200)
    public String kind = "";

    @Column(name = "config_value", length = 500)
    public String value = "";

    public boolean enabled;
  }

  /** attachments 持久化记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Entity(name = "Attachment")
  @Table(name = "attachments")
  public static class Attachment extends Scoped {
    public Long orderId;

    @Column(length = 200)
    public String originalName = "";

    @Column(length = 120)
    @JsonIgnore
    public String storageName = "";

    @Column(length = 2000)
    public String contentType = "";

    public long size;
  }
}
