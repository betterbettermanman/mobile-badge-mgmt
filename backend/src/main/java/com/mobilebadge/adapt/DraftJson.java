package com.mobilebadge.adapt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobilebadge.adapt.LlmAdapter.Extraction;
import com.mobilebadge.adapt.LlmAdapter.NeedDraft;
import com.mobilebadge.adapt.LlmAdapter.ProfileDraft;
import java.util.ArrayList;
import java.util.List;

/** 校验百炼返回的 JSON。忽略任何确认状态字段，调用方只能落草案。 */
public final class DraftJson {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private DraftJson() {
  }

  public static Extraction parse(String raw) {
    String json = stripFence(raw);
    final JsonNode root;
    try {
      root = MAPPER.readTree(json);
    } catch (Exception ex) {
      throw new VendorCallException("模型未返回可解析的 JSON", false);
    }
    if (root == null || !root.isObject()) {
      throw new VendorCallException("模型未返回 JSON 对象", false);
    }
    JsonNode profile = root.get("profile");
    if (profile == null || !profile.isObject()) {
      throw new VendorCallException("模型 JSON 缺少 profile 对象", false);
    }
    ProfileDraft profileDraft = new ProfileDraft(
        text(profile, "appellation"),
        text(profile, "intent"),
        text(profile, "budget"),
        text(profile, "decisionMaker"),
        text(profile, "notes"));
    JsonNode needsNode = root.get("needs");
    List<NeedDraft> needs = new ArrayList<>();
    if (needsNode != null && !needsNode.isNull()) {
      if (!needsNode.isArray()) {
        throw new VendorCallException("模型 JSON 的 needs 必须是数组", false);
      }
      for (JsonNode item : needsNode) {
        if (item == null || !item.isObject()) {
          throw new VendorCallException("模型 JSON 的需求项必须是对象", false);
        }
        String title = text(item, "title");
        if (title.isBlank()) {
          continue;
        }
        needs.add(new NeedDraft(title, text(item, "detail")));
      }
    }
    return new Extraction(profileDraft, List.copyOf(needs));
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return "";
    }
    if (!value.isTextual()) {
      throw new VendorCallException("模型 JSON 字段 " + field + " 必须是字符串", false);
    }
    return value.asText("").trim();
  }

  private static String stripFence(String raw) {
    if (raw == null) {
      return "";
    }
    String trimmed = raw.trim();
    if (trimmed.startsWith("```")) {
      int firstLine = trimmed.indexOf('\n');
      int lastFence = trimmed.lastIndexOf("```");
      if (firstLine > 0 && lastFence > firstLine) {
        return trimmed.substring(firstLine + 1, lastFence).trim();
      }
    }
    return trimmed;
  }
}
