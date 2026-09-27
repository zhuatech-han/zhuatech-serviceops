// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import static cn.zhuatech.serviceops.Access.error;
import static cn.zhuatech.serviceops.Models.*;

import jakarta.persistence.EntityManager;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 工单附件限制与授权下载，随机存储名防止路径穿越。知华科技 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
@Service
public class Files {
  final Path root;
  final EntityManager em;
  final Access access;
  final Catalog catalog;

  public Files(
      @Value("${serviceops.files}") String directory, EntityManager em, Access a, Catalog c) {
    root = Path.of(directory).toAbsolutePath().normalize();
    this.em = em;
    access = a;
    catalog = c;
  }

  /** 仅执行人员上传受限图片或 PDF，文件大小和文件签名均核对。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional
  public Attachment upload(long orderId, MultipartFile file) throws java.io.IOException {
    Account u = access.require("orders.execute");
    WorkOrder o = access.find(WorkOrder.class, orderId);
    if (Set.of("CLOSED", "CANCELLED").contains(o.state)) throw error(409, "已关闭工单不能新增附件");
    if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw error(400, "文件须小于5MB且不为空");
    byte[] bytes = file.getBytes();
    String type;
    if (bytes.length > 8
        && bytes[0] == (byte) 137
        && bytes[1] == 80
        && bytes[2] == 78
        && bytes[3] == 71
        && bytes[4] == 13
        && bytes[5] == 10
        && bytes[6] == 26
        && bytes[7] == 10) type = "image/png";
    else if (bytes.length > 3
        && bytes[0] == (byte) 255
        && bytes[1] == (byte) 216
        && bytes[2] == (byte) 255) type = "image/jpeg";
    else if (bytes.length > 5
        && new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
      type = "application/pdf";
    else throw error(400, "仅支持有效签名的 PNG、JPEG、PDF");
    Attachment a = new Attachment();
    a.orgId = o.orgId;
    a.orderId = o.id;
    a.storageName = UUID.randomUUID() + ".bin";
    a.originalName =
        Objects.toString(file.getOriginalFilename(), "附件").replaceAll("[\\p{Cntrl}/\\\\]", "_");
    if (a.originalName.length() > 200) a.originalName = a.originalName.substring(0, 200);
    a.contentType = type;
    a.size = bytes.length;
    java.nio.file.Files.createDirectories(root);
    Path path = root.resolve(a.storageName);
    java.nio.file.Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    try {
      em.persist(a);
      em.flush();
    } catch (RuntimeException e) {
      java.nio.file.Files.deleteIfExists(path);
      throw e;
    }
    catalog.audit(u, "FILE_UPLOAD", "orders:" + o.id, "附件:" + a.id);
    return a;
  }

  /** 下载前复核对应工单权限，不信任客户端路径。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Attachment metadata(long id) {
    access.require("orders.read");
    Attachment a = em.find(Attachment.class, id);
    if (a == null) throw error(404, "附件不存在");
    access.find(WorkOrder.class, a.orderId);
    return a;
  }

  /** 核对随机文件名与存储根目录，拒绝缺失文件及路径穿越。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Path path(Attachment a) {
    if (!a.storageName.matches("[0-9a-f-]{36}\\.bin")) throw error(404, "附件不存在");
    Path p = root.resolve(a.storageName).normalize();
    if (!p.startsWith(root) || !java.nio.file.Files.isRegularFile(p))
      throw error(404, "附件文件不存在，请核对备份");
    return p;
  }
}
