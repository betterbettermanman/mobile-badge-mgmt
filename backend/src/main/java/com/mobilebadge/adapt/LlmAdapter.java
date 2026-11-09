package com.mobilebadge.adapt;

import java.util.List;

/** 抽取适配器。产出只能当草案，调用方不得直接写成已确认。 */
public interface LlmAdapter {

  ProfileDraft extractProfile(String customerLabel, String transcript);

  List<NeedDraft> extractNeeds(String transcript);

  default Extraction extract(String customerLabel, String transcript) {
    return new Extraction(extractProfile(customerLabel, transcript), extractNeeds(transcript));
  }

  record ProfileDraft(String appellation, String intent, String budget, String decisionMaker, String notes) {
  }

  record NeedDraft(String title, String detail) {
  }

  record Extraction(ProfileDraft profile, List<NeedDraft> needs) {
  }
}
